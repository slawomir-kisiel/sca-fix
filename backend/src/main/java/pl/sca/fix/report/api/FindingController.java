package pl.sca.fix.report.api;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import pl.sca.fix.report.application.OccurrenceSelectionService;
import pl.sca.fix.report.api.OccurrenceSelectionDtos.OccurrenceOptionDto;
import pl.sca.fix.report.application.TargetVersionService;
import pl.sca.fix.report.api.TargetVersionDtos.TargetVersionsDto;
import pl.sca.fix.report.domain.VulnerabilityFinding;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.report.api.FindingDtos.OccurrenceDto;
import pl.sca.fix.report.api.FindingDtos.AffectedArtifactDto;
import pl.sca.fix.report.api.FindingDtos.FindingDetailsDto;
import pl.sca.fix.report.api.FindingDtos.FindingListItemDto;
import pl.sca.fix.report.api.FindingDtos.FindingGroupItemDto;
import pl.sca.fix.report.api.FindingDtos.PlanItemLinkDto;
import pl.sca.fix.report.api.FindingDtos.VulnerableArtifactGroupDto;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.report.persistence.PlanItemRepository;
import pl.sca.fix.report.persistence.UpdatePlanRepository;
import pl.sca.fix.report.application.PlanPreparationService;
import pl.sca.fix.report.api.PlanPreparationDtos.PreparationRunDto;
import pl.sca.fix.integration.maven.MavenMetadataClient.ProxySettings;

@RestController
@RequestMapping("/api")
public class FindingController {
    private final VulnerabilityFindingRepository findings;
    private final SecurityScanReportRepository reports;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final OccurrenceSelectionService occurrenceSelectionService;
    private final TargetVersionService targetVersionService;
    private final ArtifactTreeNodeRepository treeNodes;
    private final PlanItemRepository planItems;
    private final PlanPreparationService planPreparation;
    private final UpdatePlanRepository updatePlans;

    public FindingController(VulnerabilityFindingRepository findings, SecurityScanReportRepository reports,
                             DependencyTreeOccurrenceRepository occurrences,
                             OccurrenceSelectionService occurrenceSelectionService,
                             TargetVersionService targetVersionService,
                             ArtifactTreeNodeRepository treeNodes,
                             PlanItemRepository planItems, PlanPreparationService planPreparation,
                             UpdatePlanRepository updatePlans) {
        this.findings = findings; this.reports = reports; this.occurrences = occurrences;
        this.occurrenceSelectionService = occurrenceSelectionService;
        this.targetVersionService = targetVersionService;
        this.treeNodes = treeNodes;
        this.planItems = planItems; this.planPreparation = planPreparation; this.updatePlans = updatePlans;
    }

    @PostMapping("/reports/{reportId}/plan-preparation")
    public PreparationRunDto preparePlan(@PathVariable UUID reportId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String severity,
                                         @RequestParam(required = false) Boolean planned) {
        return planPreparation.start(reportId, q, severity, planned);
    }

    @GetMapping("/reports/{reportId}/plan-preparation/{runId}")
    public PreparationRunDto planPreparation(@PathVariable UUID reportId, @PathVariable UUID runId) {
        PreparationRunDto result = planPreparation.details(runId);
        if (!reportId.equals(result.reportId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan preparation run not found");
        return result;
    }

    @GetMapping("/reports/{reportId}/findings")
    @Transactional(readOnly = true)
    public List<FindingListItemDto> list(@PathVariable UUID reportId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String severity) {
        if (!reports.existsById(reportId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        return findings.findByReportIdOrderBySeverityAscExternalIdAsc(reportId).stream()
                .filter(item -> status == null || status.equalsIgnoreCase(item.getStatus().name()))
                .filter(item -> severity == null || severity.equalsIgnoreCase(item.getSeverity()))
                .map(this::listItem).toList();
    }

    @GetMapping("/reports/{reportId}/vulnerable-artifacts")
    @Transactional(readOnly = true)
    public List<VulnerableArtifactGroupDto> vulnerableArtifacts(@PathVariable UUID reportId,
                                                                 @RequestParam(required = false) String q,
                                                                 @RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String severity,
                                                                 @RequestParam(required = false) Boolean planned) {
        var report = reports.findById(reportId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<VulnerabilityFinding> reportFindings = findings.findByReportIdOrderBySeverityAscExternalIdAsc(reportId);
        UUID activePlanId = updatePlans.findByRepositoryIdAndActiveTrue(report.getRepositoryId()).map(item -> item.getId()).orElse(null);
        Map<UUID, PlanItemLinkDto> findingPlanItems = planItems.findByFindingIdIn(reportFindings.stream().map(VulnerabilityFinding::getId).toList()).stream()
                .filter(item -> activePlanId != null && activePlanId.equals(item.getUpdatePlanId()))
                .collect(java.util.stream.Collectors.toMap(item -> item.getFindingId(), this::planItemLink, (left, right) -> right));
        Map<GroupKey, List<FindingGroupItemDto>> grouped = new LinkedHashMap<>();
        for (VulnerabilityFinding finding : reportFindings) {
            if (status != null && !status.equalsIgnoreCase(finding.getStatus().name())) continue;
            if (severity != null && !severity.equalsIgnoreCase(finding.getSeverity())) continue;

            var artifact = finding.getAffectedArtifacts().stream().findFirst().orElse(null);
            GroupKey key = GroupKey.from(artifact);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(groupItem(finding, findingPlanItems.get(finding.getId())));
        }

        return grouped.entrySet().stream()
                .map(entry -> groupDto(entry.getKey(), entry.getValue(), query))
                .filter(group -> !group.findings().isEmpty())
                .filter(group -> planned == null || (planned == (group.planItem() != null)))
                .sorted(Comparator.comparing(VulnerableArtifactGroupDto::groupId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(VulnerableArtifactGroupDto::artifactId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(VulnerableArtifactGroupDto::version, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private VulnerableArtifactGroupDto groupDto(GroupKey key, List<FindingGroupItemDto> allFindings, String query) {
        boolean groupMatches = !query.isEmpty() && (contains(key.groupId(), query) || contains(key.artifactId(), query)
                || contains(key.version(), query) || contains(key.coordinates(), query));
        List<FindingGroupItemDto> visibleFindings = query.isEmpty() || groupMatches
                ? allFindings
                : allFindings.stream().filter(item -> matches(query, key, item)).toList();
        FindingGroupItemDto highest = allFindings.stream()
                .filter(item -> item.cvssScore() != null)
                .max(Comparator.comparing(FindingGroupItemDto::cvssScore)
                        .thenComparingInt(item -> severityRank(item.severity())))
                .orElse(null);
        PlanItemLinkDto planItem = highest != null && highest.planItem() != null ? highest.planItem()
                : allFindings.stream().map(FindingGroupItemDto::planItem).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return new VulnerableArtifactGroupDto(key.groupId(), key.artifactId(), key.version(),
                allFindings.stream().mapToLong(FindingGroupItemDto::occurrenceCount).sum(), allFindings.size(),
                highest == null ? null : highest.cvssScore(), highest == null ? null : highest.severity(),
                highest == null ? null : highest.findingId(), planItem,
                visibleFindings.stream().sorted(FINDING_GROUP_ORDER).toList());
    }

    private static final Comparator<FindingGroupItemDto> FINDING_GROUP_ORDER =
            Comparator.comparingInt((FindingGroupItemDto item) -> severityRank(item.severity())).reversed()
                    .thenComparing(FindingGroupItemDto::cvssScore,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(FindingGroupItemDto::cve, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(FindingGroupItemDto::externalId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private FindingGroupItemDto groupItem(VulnerabilityFinding finding, PlanItemLinkDto planItem) {
        return new FindingGroupItemDto(finding.getId(), finding.getCve(), finding.getExternalId(),
                finding.getIdentifierType(), finding.getTitle(), finding.getSource(), finding.getSeverity(),
                finding.getCvssScore(), occurrences.countByFindingId(finding.getId()), finding.getStatus().name(), planItem);
    }

    private PlanItemLinkDto planItemLink(pl.sca.fix.report.domain.PlanItem item) {
        return new PlanItemLinkDto(item.getId(), item.getUpdatePlanId(), item.getFindingId(), item.getOccurrenceId(),
                item.getTargetProjectId(), item.getChangePointTreeNodeId(), item.getTargetArtifactVersionId(),
                item.getStrategy().name(), item.getStatus().name());
    }

    private boolean matches(String query, GroupKey key, FindingGroupItemDto item) {
        return contains(key.groupId(), query) || contains(key.artifactId(), query) || contains(key.version(), query)
                || contains(key.coordinates(), query) || contains(item.cve(), query) || contains(item.externalId(), query)
                || contains(item.title(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static int severityRank(String severity) {
        if (severity == null) return -1;
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private record GroupKey(String groupId, String artifactId, String version) {
        static GroupKey from(pl.sca.fix.report.domain.AffectedArtifact artifact) {
            if (artifact == null || artifact.getGroupId() == null || artifact.getArtifactId() == null) {
                return new GroupKey(null, null, null);
            }
            return new GroupKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getReportedVersion());
        }

        String coordinates() {
            if (groupId == null || artifactId == null) return "unknown artifact";
            return groupId + ":" + artifactId + ":" + (version == null ? "" : version);
        }
    }

    @GetMapping("/findings/{findingId}")
    @Transactional(readOnly = true)
    public FindingDetailsDto details(@PathVariable UUID findingId) {
        return findings.findById(findingId).map(this::details)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));
    }

    @GetMapping("/findings/{findingId}/occurrences")
    public List<OccurrenceOptionDto> occurrenceOptions(@PathVariable UUID findingId) {
        return occurrenceSelectionService.list(findingId);
    }

    @GetMapping("/findings/{findingId}/target-versions")
      public TargetVersionsDto targetVersions(@PathVariable UUID findingId, @RequestParam UUID occurrenceId,
                                              @RequestParam(required = false) UUID changePointArtifactVersionId,
                                              @RequestParam(required = false) String version,
                                              @RequestParam(required = false) String proxyHost,
                                              @RequestParam(required = false) Integer proxyPort,
                                              @RequestParam(required = false) String proxyUsername,
                                              @RequestParam(required = false) String proxyPassword) {
        ProxySettings proxySettings;
        try {
            proxySettings = ProxySettings.from(proxyHost, proxyPort, proxyUsername, proxyPassword);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return changePointArtifactVersionId == null
                ? targetVersionService.list(findingId, occurrenceId, proxySettings)
                : targetVersionService.list(findingId, occurrenceId, changePointArtifactVersionId, version, proxySettings);
    }

    private FindingListItemDto listItem(VulnerabilityFinding finding) {
        var artifact = finding.getAffectedArtifacts().stream().findFirst().orElse(null);
        return new FindingListItemDto(finding.getId(), finding.getExternalId(), finding.getIdentifierType(), finding.getSource(),
                finding.getSeverity(), finding.getCvssScore(), coordinates(artifact), artifact == null ? null : artifact.getReportedVersion(), occurrences.countByFindingId(finding.getId()), finding.getStatus().name());
    }

    private FindingDetailsDto details(VulnerabilityFinding finding) {
        return new FindingDetailsDto(finding.getId(), finding.getExternalId(), finding.getIdentifierType(), finding.getSource(),
                finding.getCve(), finding.getTitle(), finding.getSeverity(), finding.getDescription(), finding.getRecommendation(),
                finding.getCvssVersion(), finding.getCvssScore(), finding.getCvssVector(),
                List.copyOf(finding.getCwes()), List.copyOf(finding.getReferences()),
                finding.getAffectedArtifacts().stream().map(item -> new AffectedArtifactDto(item.getId(), item.getPurl(), item.getGroupId(),
                        item.getArtifactId(), item.getReportedVersion(), item.getFileName(), item.getFilePath(), item.getSha1(), item.getSha256())).toList(),
                occurrences.findByFindingId(finding.getId()).stream().map(item -> new OccurrenceDto(item.getId(), item.getProjectId(),
                        treeNodes.findById(item.getVulnerableArtifactTreeNodeId()).map(node -> node.getArtifactVersionId()).orElse(null),
                        item.getResolvedVersion(), item.getDefinitionSource(), item.isSourcesAvailable())).toList(), finding.getStatus().name());
    }

    private String coordinates(pl.sca.fix.report.domain.AffectedArtifact artifact) {
        return artifact == null || artifact.getGroupId() == null || artifact.getArtifactId() == null ? null
                : artifact.getGroupId() + ":" + artifact.getArtifactId();
    }
}
