package pl.sca.fix.report.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.sca.fix.artifact.domain.ArtifactVersion;
import pl.sca.fix.artifact.application.ArtifactVersionWriteCoordinator;
import pl.sca.fix.artifact.domain.VersionRule;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.artifact.persistence.VersionRuleRepository;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.integration.maven.MavenMetadataClient;
import pl.sca.fix.integration.maven.MavenVersionLookup;
import pl.sca.fix.integration.maven.MavenVersionComparator;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.repository.persistence.RepositoryRepository;
import pl.sca.fix.report.api.TargetVersionDtos.TargetVersionOptionDto;
import pl.sca.fix.report.api.TargetVersionDtos.TargetVersionsDto;
import pl.sca.fix.report.api.TargetVersionDtos.VersionRuleDto;
import pl.sca.fix.report.api.TargetVersionDtos.TargetContextDto;
import pl.sca.fix.report.domain.DependencyTreeOccurrence;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;

@Service
public class TargetVersionService {
    private final VulnerabilityFindingRepository findings;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final ArtifactRepository artifacts;
    private final ArtifactVersionRepository versions;
    private final VersionRuleRepository rules;
    private final MavenMetadataClient mavenMetadataClient;
    private final ProjectRepository projects;
    private final RepositoryRepository repositories;
    private final ArtifactTreeNodeRepository nodes;
    private final ArtifactVersionWriteCoordinator versionWrites;

    public TargetVersionService(VulnerabilityFindingRepository findings, DependencyTreeOccurrenceRepository occurrences,
                                ArtifactRepository artifacts,
                                ArtifactVersionRepository versions, VersionRuleRepository rules,
                                MavenMetadataClient mavenMetadataClient,
                                ProjectRepository projects, RepositoryRepository repositories, ArtifactTreeNodeRepository nodes,
                                ArtifactVersionWriteCoordinator versionWrites) {
        this.findings = findings; this.occurrences = occurrences;
        this.artifacts = artifacts; this.versions = versions; this.rules = rules;
        this.mavenMetadataClient = mavenMetadataClient;
        this.projects = projects; this.repositories = repositories; this.nodes = nodes; this.versionWrites = versionWrites;
    }

    @Transactional
    public TargetVersionsDto list(UUID findingId, UUID occurrenceId) {
        var occurrence = occurrence(findingId, occurrenceId);
        return list(findingId, occurrenceId, nodeVersionId(occurrence), null);
    }

    @Transactional
    public TargetVersionsDto list(UUID findingId, UUID occurrenceId, UUID changePointArtifactVersionId) {
        return list(findingId, occurrenceId, changePointArtifactVersionId, null);
    }

    @Transactional
    public TargetVersionsDto list(UUID findingId, UUID occurrenceId, UUID changePointArtifactVersionId, String requestedVersion) {
        var finding = finding(findingId);
        var occurrence = occurrence(findingId, occurrenceId);
        var changePoint = versions.findById(changePointArtifactVersionId)
                .orElseThrow(() -> notFound("Change point artifact version not found"));
        var artifact = artifacts.findById(changePoint.getArtifactId())
                .orElseThrow(() -> notFound("Artifact not found"));
        var affected = finding.getAffectedArtifacts().stream()
                .filter(item -> item.getGroupId() != null && item.getArtifactId() != null).findFirst().orElse(null);
        var project = projects.findById(occurrence.getProjectId()).orElseThrow(() -> notFound("Project not found"));
        var repository = repositories.findById(project.getRepositoryId()).orElseThrow(() -> notFound("Repository not found"));
        var rule = rules.findByArtifactId(artifact.getId()).orElse(null);
        String currentVersion = changePoint.getVersion();
        Map<String, TargetVersionOptionDto> candidates = new LinkedHashMap<>();
        versions.findByArtifactIdIn(List.of(artifact.getId())).stream()
                .sorted(Comparator.comparing(ArtifactVersion::getVersion, Comparator.nullsLast(MavenVersionComparator::compareVersions)).reversed())
                .map(item -> option(item.getId(), item.getVersion(), changePointArtifactVersionId, rule, "LOCAL_GRAPH", null,
                        artifact.getGroupId(), artifact.getArtifactId()))
                .forEach(item -> candidates.put(item.version(), item));
        MavenVersionLookup remote = mavenMetadataClient.findVersions(artifact.getGroupId(), artifact.getArtifactId());
        remote.versions().forEach(item -> candidates.putIfAbsent(item.version(),
                option(persistCandidateVersion(artifact.getId(), item.version()), item.version(), changePointArtifactVersionId, rule,
                        "MAVEN_REPOSITORY", item.repositoryId(), artifact.getGroupId(), artifact.getArtifactId())));
        if (requestedVersion != null && !requestedVersion.isBlank() && !candidates.containsKey(requestedVersion.trim())) {
            String custom = requestedVersion.trim();
            candidates.put(custom, option(persistCandidateVersion(artifact.getId(), custom), custom, changePointArtifactVersionId, rule,
                    "USER_INPUT", null, artifact.getGroupId(), artifact.getArtifactId()));
        }
        List<TargetVersionOptionDto> sorted = candidates.values().stream()
                .sorted(Comparator.comparing((TargetVersionOptionDto item) -> !MavenVersionComparator.sameDevelopmentLine(currentVersion, item.version()))
                        .thenComparing(TargetVersionOptionDto::version, Comparator.nullsLast(MavenVersionComparator::compareVersions).reversed()))
                .toList();
        UUID recommended = sorted.stream()
                .filter(TargetVersionOptionDto::eligible)
                .filter(item -> !item.current())
                .filter(item -> MavenVersionComparator.sameDevelopmentLine(currentVersion, item.version()))
                .filter(item -> MavenVersionComparator.compareVersions(item.version(), currentVersion) > 0)
                .sorted(Comparator.comparing((TargetVersionOptionDto item) -> !MavenVersionComparator.sameQualifier(currentVersion, item.version()))
                        .thenComparing(TargetVersionOptionDto::version, Comparator.nullsLast(MavenVersionComparator::compareVersions).reversed()))
                .map(TargetVersionOptionDto::artifactVersionId).findFirst().orElse(null);
        return new TargetVersionsDto(findingId, occurrenceId, affected == null ? occurrence.getResolvedVersion() : affected.getReportedVersion(), currentVersion,
                rule == null ? null : new VersionRuleDto(artifact.getId(), rule.getPattern(), rule.isAllowSnapshots()),
                sorted, recommended,
                remote.warnings(), new TargetContextDto(repository.getId().toString(), repository.getUrl(), project.getName(), project.getPomPath(),
                        artifact.getGroupId(), artifact.getArtifactId(), changePoint.getVersion(), occurrence.getResolvedVersion()));
    }

    private TargetVersionOptionDto option(UUID id, String version, UUID currentArtifactVersionId, VersionRule rule,
                                          String source, String repositoryId, String groupId, String artifactId) {
        boolean current = id.equals(currentArtifactVersionId);
        String reason = null;
        if (version != null && version.toUpperCase().endsWith("-SNAPSHOT") && (rule == null || !rule.isAllowSnapshots())) reason = "SNAPSHOT_NOT_ALLOWED";
        if (reason == null && rule != null && rule.getPattern() != null && !rule.getPattern().isBlank()) {
            try { if (version == null || !Pattern.compile(rule.getPattern()).matcher(version).matches()) reason = "VERSION_RULE_PATTERN"; }
            catch (PatternSyntaxException exception) { reason = "INVALID_VERSION_RULE"; }
        }
        return new TargetVersionOptionDto(id, version, current, reason == null, reason, source, repositoryId,
                "https://mvnrepository.com/artifact/" + urlPart(groupId) + "/" + urlPart(artifactId) + "/" + urlPart(version));
    }

    private String urlPart(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private UUID persistCandidateVersion(UUID artifactId, String version) {
        return versionWrites.execute(() -> versions.findByArtifactIdAndVersion(artifactId, version)
                .orElseGet(() -> versions.save(new ArtifactVersion(UUID.randomUUID(), artifactId, version))).getId());
    }

    private pl.sca.fix.report.domain.VulnerabilityFinding finding(UUID id) { return findings.findById(id).orElseThrow(() -> notFound("Finding not found")); }
    private UUID nodeVersionId(DependencyTreeOccurrence occurrence) {
        return nodes.findById(occurrence.getVulnerableArtifactTreeNodeId()).map(item -> item.getArtifactVersionId())
                .orElseThrow(() -> notFound("Occurrence tree node not found"));
    }
    private DependencyTreeOccurrence occurrence(UUID findingId, UUID id) {
        var item = occurrences.findById(id).orElseThrow(() -> notFound("Occurrence not found"));
        if (!findingId.equals(item.getFindingId())) throw badRequest("Occurrence does not belong to finding");
        return item;
    }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
