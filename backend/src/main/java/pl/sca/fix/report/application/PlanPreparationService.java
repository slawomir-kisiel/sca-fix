package pl.sca.fix.report.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.report.api.PlanPreparationDtos.PreparationItemDto;
import pl.sca.fix.report.api.PlanPreparationDtos.PreparationRunDto;
import pl.sca.fix.report.api.StrategyDtos.StrategyOptionDto;
import pl.sca.fix.report.api.TargetVersionDtos.TargetVersionsDto;
import pl.sca.fix.report.domain.PlanItem;
import pl.sca.fix.report.domain.PlanPreparationItem;
import pl.sca.fix.report.domain.PlanPreparationRun;
import pl.sca.fix.report.domain.UpdatePlan;
import pl.sca.fix.report.domain.UpdateStrategy;
import pl.sca.fix.report.domain.VulnerabilityFinding;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.report.persistence.PlanItemRepository;
import pl.sca.fix.report.persistence.PlanPreparationItemRepository;
import pl.sca.fix.report.persistence.PlanPreparationRunRepository;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;
import pl.sca.fix.report.persistence.UpdatePlanRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;

@Service
public class PlanPreparationService {
    private final SecurityScanReportRepository reports;
    private final VulnerabilityFindingRepository findings;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final PlanItemRepository planItems;
    private final PlanPreparationRunRepository runs;
    private final PlanPreparationItemRepository items;
    private final UpdatePlanRepository plans;
    private final JobService jobs;
    private final ChangePointGraphService graph;
    private final TargetVersionService targetVersions;
    private final StrategySelectionService strategies;
    private final ArtifactTreeNodeRepository nodes;
    private final ArtifactVersionRepository versions;
    private final Executor executor;
    private final TransactionTemplate transactionTemplate;

    public PlanPreparationService(SecurityScanReportRepository reports, VulnerabilityFindingRepository findings,
                                  DependencyTreeOccurrenceRepository occurrences, PlanItemRepository planItems,
                                  PlanPreparationRunRepository runs, PlanPreparationItemRepository items,
                                  UpdatePlanRepository plans, JobService jobs, ChangePointGraphService graph,
                                  TargetVersionService targetVersions, StrategySelectionService strategies,
                                  ArtifactTreeNodeRepository nodes, ArtifactVersionRepository versions,
                                  @Qualifier("mavenAnalysisExecutor") Executor executor,
                                  org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.reports = reports; this.findings = findings; this.occurrences = occurrences; this.planItems = planItems;
        this.runs = runs; this.items = items; this.plans = plans; this.jobs = jobs; this.graph = graph;
        this.targetVersions = targetVersions; this.strategies = strategies; this.nodes = nodes; this.versions = versions;
        this.executor = executor; this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public PreparationRunDto start(UUID reportId, String query, String severity, Boolean planned) {
        var report = reports.findById(reportId).orElseThrow(() -> notFound("Report not found"));
        UpdatePlan plan = plans.findByRepositoryIdAndActiveTrue(report.getRepositoryId())
                .filter(item -> item.getStatus().name().equals("PLANNED"))
                .orElseThrow(() -> badRequest("An active PLANNED update plan is required"));
        List<GroupWork> groups = groups(reportId, query, severity);
        Map<UUID, PlanItem> activeItems = planItems.findByUpdatePlanIdOrderBySelectedAtAsc(plan.getId()).stream()
                .collect(Collectors.toMap(PlanItem::getFindingId, Function.identity(), (left, right) -> right));
        PlanPreparationRun run = runs.save(new PlanPreparationRun(reportId, plan.getId()));
        List<UUID> queuedItems = new ArrayList<>();
        for (GroupWork group : groups) {
            boolean alreadyPlanned = group.findingIds().stream().anyMatch(activeItems::containsKey);
            if (alreadyPlanned || Boolean.TRUE.equals(planned)) continue;
            var job = jobs.create("AUTOMATIC_PLAN_PREPARATION");
            PlanPreparationItem item = items.save(new PlanPreparationItem(run.getId(), group.groupId(), group.artifactId(), group.version(), group.maxCvss(), job.getId()));
            queuedItems.add(item.getId());
        }
        if (items.findByRunIdOrderByCreatedAtAsc(run.getId()).isEmpty()) run.complete();
        runs.save(run);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { queuedItems.forEach(itemId -> executor.execute(() -> process(itemId, run.getId()))); }
        });
        return details(run.getId());
    }

    @Transactional(readOnly = true)
    public PreparationRunDto details(UUID runId) {
        PlanPreparationRun run = runs.findById(runId).orElseThrow(() -> notFound("Plan preparation run not found"));
        return new PreparationRunDto(run.getId(), run.getReportId(), run.getUpdatePlanId(), run.getStatus(), run.getCreatedAt(), run.getCompletedAt(),
                items.findByRunIdOrderByCreatedAtAsc(runId).stream().map(this::item).toList());
    }

    private void process(UUID itemId, UUID runId) {
        transactionTemplate.executeWithoutResult(ignored -> processWithinTransaction(itemId, runId));
    }

    private void processWithinTransaction(UUID itemId, UUID runId) {
        PlanPreparationItem item = items.findById(itemId).orElse(null);
        if (item == null) return;
        item.start(); items.save(item); jobs.start(item.getJobId(), "PLANNING");
        try {
            GroupWork group = new GroupWork(item.getGroupId(), item.getArtifactId(), item.getVersion(), item.getMaxCvssScore(),
                    findings.findByReportIdOrderBySeverityAscExternalIdAsc(runs.findById(runId).orElseThrow().getReportId()).stream()
                            .filter(f -> matches(f, item.getGroupId(), item.getArtifactId(), item.getVersion())).toList().stream().map(VulnerabilityFinding::getId).toList());
            Decision decision = findDecision(group);
            if (decision == null) {
                item.skipped("Nie znaleziono automatycznie obsługiwalnego miejsca zmiany");
                jobs.finish(item.getJobId(), JobStatus.SUCCESS, 0, "NO_AUTOMATIC_STRATEGY", item.getMessage());
            } else {
                UpdatePlan plan = plans.findById(runs.findById(runId).orElseThrow().getUpdatePlanId()).orElseThrow();
                planItems.save(new PlanItem(plan.getId(), decision.findingId(), decision.occurrenceId(), decision.projectId(), decision.treeNodeId(), decision.strategy(), decision.targetVersionId()));
                item.planned(decision.findingId(), decision.occurrenceId());
                jobs.appendOutput(item.getJobId(), 1, "STDOUT", "PlanItem utworzony dla " + item.getGroupId() + ":" + item.getArtifactId() + ":" + item.getVersion());
                jobs.finish(item.getJobId(), JobStatus.SUCCESS, 0, null, null);
            }
        } catch (Exception exception) {
            item.failed(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            jobs.finish(item.getJobId(), JobStatus.FAILED, null, "AUTOMATIC_PLANNING_FAILED", item.getMessage());
        }
        items.save(item); completeIfDone(runId);
    }

    @Transactional
    void completeIfDone(UUID runId) {
        PlanPreparationRun run = runs.findById(runId).orElse(null);
        if (run == null || items.findByRunIdOrderByCreatedAtAsc(runId).stream().anyMatch(item -> item.getStatus().name().equals("QUEUED") || item.getStatus().name().equals("PLANNING"))) return;
        run.complete(); runs.save(run);
    }

    private Decision findDecision(GroupWork group) {
        List<VulnerabilityFinding> candidates = new ArrayList<>();
        findings.findAllById(group.findingIds()).forEach(candidates::add);
        candidates = candidates.stream()
                .sorted(Comparator.comparing(VulnerabilityFinding::getCvssScore, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        for (VulnerabilityFinding finding : candidates) {
            for (var occurrence : occurrences.findByFindingId(finding.getId())) {
                for (var project : graph.targetProjects(finding.getId(), occurrence.getId()).stream()
                        .sorted(Comparator.comparing(item -> !item.recommended())).toList()) {
                    try {
                        var focused = graph.get(finding.getId(), occurrence.getId(), project.projectId());
                        for (var node : focused.nodes()) {
                            if (project.projectId().equals(node.projectId())) continue;
                            TargetVersionsDto versionsDto = targetVersions.list(finding.getId(), occurrence.getId(), node.artifactVersionId(), null);
                            UUID targetId = versionsDto.selectedArtifactVersionId();
                            if (targetId == null) continue;
                            var strategyOptions = strategies.listForTreeNode(finding.getId(), occurrence.getId(), project.projectId(), node.treeNodeId(), targetId);
                            var option = strategyOptions
                                    .options().stream().filter(StrategyOptionDto::eligible)
                                    .filter(item -> item.strategy() != UpdateStrategy.DEFER && item.strategy() != UpdateStrategy.REJECT)
                                    .sorted(Comparator.comparing(item -> item.strategy() == strategyOptions.recommendedStrategy() ? 0 : 1))
                                    .findFirst().orElse(null);
                            if (option != null) return new Decision(finding.getId(), occurrence.getId(), project.projectId(), node.treeNodeId(), targetId, option.strategy());
                        }
                    } catch (RuntimeException ignored) { }
                }
            }
        }
        return null;
    }

    private List<GroupWork> groups(UUID reportId, String query, String severity) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<String, List<VulnerabilityFinding>> grouped = findings.findByReportIdOrderBySeverityAscExternalIdAsc(reportId).stream()
                .filter(f -> severity == null || severity.isBlank() || severity.equalsIgnoreCase(f.getSeverity()))
                .filter(f -> normalizedQuery.isBlank() || matchesQuery(f, normalizedQuery))
                .collect(Collectors.groupingBy(f -> key(f), java.util.LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream().map(list -> {
            var first = list.get(0); var artifact = first.getAffectedArtifacts().stream().findFirst().orElse(null);
            return new GroupWork(artifact == null ? null : artifact.getGroupId(), artifact == null ? null : artifact.getArtifactId(), artifact == null ? null : artifact.getReportedVersion(),
                    list.stream().map(VulnerabilityFinding::getCvssScore).filter(java.util.Objects::nonNull).max(Double::compareTo).orElse(null), list.stream().map(VulnerabilityFinding::getId).toList());
        }).toList();
    }
    private boolean matchesQuery(VulnerabilityFinding finding, String query) {
        var a = finding.getAffectedArtifacts().stream().findFirst().orElse(null);
        String coordinates = a == null ? "" : String.valueOf(a.getGroupId()) + ":" + a.getArtifactId() + ":" + a.getReportedVersion();
        return coordinates.toLowerCase(Locale.ROOT).contains(query) || String.valueOf(finding.getCve()).toLowerCase(Locale.ROOT).contains(query)
                || String.valueOf(finding.getExternalId()).toLowerCase(Locale.ROOT).contains(query) || String.valueOf(finding.getTitle()).toLowerCase(Locale.ROOT).contains(query);
    }
    private boolean matches(VulnerabilityFinding finding, String groupId, String artifactId, String version) {
        var a = finding.getAffectedArtifacts().stream().findFirst().orElse(null);
        return a != null && java.util.Objects.equals(groupId, a.getGroupId()) && java.util.Objects.equals(artifactId, a.getArtifactId()) && java.util.Objects.equals(version, a.getReportedVersion());
    }
    private String key(VulnerabilityFinding finding) { var a = finding.getAffectedArtifacts().stream().findFirst().orElse(null); return a == null ? "unknown" : String.valueOf(a.getGroupId()) + ":" + a.getArtifactId() + ":" + a.getReportedVersion(); }
    private PreparationItemDto item(PlanPreparationItem item) { return new PreparationItemDto(item.getId(), item.getGroupId(), item.getArtifactId(), item.getVersion(), item.getMaxCvssScore(), item.getJobId(), item.getFindingId(), item.getOccurrenceId(), item.getStatus(), item.getMessage(), item.getUpdatedAt()); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record GroupWork(String groupId, String artifactId, String version, Double maxCvss, List<UUID> findingIds) { }
    private record Decision(UUID findingId, UUID occurrenceId, UUID projectId, UUID treeNodeId, UUID targetVersionId, UpdateStrategy strategy) { }
}
