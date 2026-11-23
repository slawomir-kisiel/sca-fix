package pl.sca.fix.uibackend.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.discovery.ProjectDiscoveryRequest;
import pl.sca.fix.core.discovery.ProjectDiscoveryService;
import pl.sca.fix.core.model.ProjectModel;
import pl.sca.fix.core.plan.PlanArtifact;
import pl.sca.fix.core.plan.PlanDocument;
import pl.sca.fix.core.plan.PlanDocumentJsonCodec;
import pl.sca.fix.core.plan.PlanFinding;
import pl.sca.fix.core.plan.PlanIssue;
import pl.sca.fix.core.plan.PlanItem;
import pl.sca.fix.core.plan.PlanProject;
import pl.sca.fix.core.plan.PlanRepository;
import pl.sca.fix.core.report.CanonicalReportJsonCodec;
import pl.sca.fix.core.report.CorrelationJsonCodec;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.report.VulnerableLibrary;
import pl.sca.fix.uibackend.config.WorkspaceConfigurationService;

@Service
public class AutomaticPlanningService {
    private final WorkspaceRepository workspaces;
    private final ProjectDiscoveryService discovery;
    private final WorkspaceConfigurationService configurations;
    private final ConcurrentMap<UUID, PlanningJob> jobs = new ConcurrentHashMap<>();

    public AutomaticPlanningService(WorkspaceRepository workspaces, ProjectDiscoveryService discovery,
                                    WorkspaceConfigurationService configurations) {
        this.workspaces = workspaces; this.discovery = discovery; this.configurations = configurations;
    }

    public PlanningJob start(UUID workspaceId, List<String> libraries) {
        WorkspaceEntity workspace = workspace(workspaceId);
        UUID id = UUID.randomUUID();
        PlanningJob job = new PlanningJob(id, workspaceId, "QUEUED", 0, 0, null, null, Instant.now(), null);
        jobs.put(id, job); CompletableFuture.runAsync(() -> execute(id, workspace, libraries)); return job;
    }
    public PlanningJob get(UUID id) { PlanningJob job = jobs.get(id); if (job == null) throw new WorkspaceException("PLANNING_JOB_NOT_FOUND", "Planning job not found: " + id); return job; }

    private void execute(UUID id, WorkspaceEntity workspace, List<String> requestedLibraries) {
        jobs.computeIfPresent(id, (key, job) -> job.running());
        try {
            Path repository = Path.of(workspace.getRepositoryPath());
            CanonicalReportJsonCodec reportCodec = new CanonicalReportJsonCodec();
            Path reports = repository.resolve("target/scafix/reports");
            var report = reportCodec.read(Files.readAllBytes(reports.resolve("canonical-report.json")));
            CorrelationResult correlation = new CorrelationJsonCodec().read(Files.readAllBytes(reports.resolve("correlation.json")));
            List<ProjectModel> projects = discovery.discover(ProjectDiscoveryRequest.cli(repository, List.of("*"))).projects();
            List<PlanItem> items = new ArrayList<>(); List<PlanIssue> warnings = new ArrayList<>(); int skipped = 0;
            for (VulnerableLibrary library : report.vulnerableLibraries()) {
                String key = library.groupId() + ":" + library.artifactId() + ":" + library.version();
                if (requestedLibraries != null && !requestedLibraries.isEmpty() && !requestedLibraries.contains(key)) continue;
                var entry = correlation.entries().stream().filter(item -> item.libraryKey().equals(key)).findFirst().orElse(null);
                String target = library.recommendedVersion().stream().findFirst().orElse(null);
                if (entry == null || entry.occurrences().isEmpty() || target == null) { skipped++; warnings.add(new PlanIssue("NO_AUTOMATIC_PLAN", "library=" + key)); continue; }
                var occurrence = entry.occurrences().getFirst();
                ProjectModel project = projects.stream().filter(item -> item.coordinate().gav().equals(occurrence.projectId())).findFirst().orElse(null);
                if (project == null) { skipped++; warnings.add(new PlanIssue("SOURCE_PROJECT_NOT_FOUND", "projectId=" + occurrence.projectId() + " library=" + key)); continue; }
                String pom = project.pomPath().toString().replace('\\', '/');
                String itemId = UUID.nameUUIDFromBytes((pom + "|" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                items.add(new PlanItem(itemId, new PlanProject(project.coordinate().groupId(), project.coordinate().artifactId(), project.coordinate().version(), pom),
                        new PlanArtifact(library.groupId(), library.artifactId()), occurrence.resolvedVersion(), target, "CVE_OVERRIDE",
                        library.cve().isEmpty() ? null : new PlanFinding(library.cve().getFirst(), "CANONICAL_REPORT", occurrence.pathId()),
                        "unknown", "unknown", "PLANNED", List.of(), List.of()));
            }
            PlanDocument plan = new PlanDocument(1, "ui-" + id, Instant.now(), new PlanRepository(repository.toString(), "unknown"),
                    UpdateLevel.parse(configurations.get(workspace.getId()).updateLevel()), items, warnings, List.of(), "PLANNED");
            Path output = reports.resolve("plan.json"); Files.write(output, new PlanDocumentJsonCodec().write(plan));
            int plannedCount = items.size();
            int skippedCount = skipped;
            String outputPath = output.toString();
            jobs.computeIfPresent(id, (key, job) -> job.completed(plannedCount, skippedCount, outputPath, Instant.now()));
        } catch (Exception exception) { jobs.computeIfPresent(id, (key, job) -> job.failed(exception.getMessage(), Instant.now())); }
    }
    private WorkspaceEntity workspace(UUID id) { return workspaces.findById(id).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + id)); }
}
