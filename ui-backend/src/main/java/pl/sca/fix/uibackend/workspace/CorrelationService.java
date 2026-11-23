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
import pl.sca.fix.core.maven.DependencyTree;
import pl.sca.fix.core.maven.JsonDependencyTreeParser;
import pl.sca.fix.core.model.ProjectModel;
import pl.sca.fix.core.report.CanonicalReportJsonCodec;
import pl.sca.fix.core.report.CanonicalScanReport;
import pl.sca.fix.core.report.CorrelationJsonCodec;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.ProjectTree;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.uibackend.config.WorkspaceConfigurationService;

@Service
public class CorrelationService {
    private final WorkspaceRepository workspaces;
    private final ProjectDiscoveryService discovery;
    private final WorkspaceConfigurationService configurations;
    private final ConcurrentMap<UUID, CorrelationJob> jobs = new ConcurrentHashMap<>();
    private final pl.sca.fix.core.report.CorrelationService core = new pl.sca.fix.core.report.CorrelationService();
    private final JsonDependencyTreeParser trees = new JsonDependencyTreeParser();

    public CorrelationService(WorkspaceRepository workspaces, ProjectDiscoveryService discovery,
                              WorkspaceConfigurationService configurations) {
        this.workspaces = workspaces; this.discovery = discovery; this.configurations = configurations;
    }

    public CorrelationJob start(UUID workspaceId) {
        WorkspaceEntity workspace = workspace(workspaceId);
        UUID jobId = UUID.randomUUID();
        CorrelationJob queued = new CorrelationJob(jobId, workspaceId, "QUEUED", 0, 0, 0, 0, 0, "", "", null, Instant.now(), null);
        jobs.put(jobId, queued);
        CompletableFuture.runAsync(() -> execute(jobId, workspace));
        return queued;
    }

    public CorrelationJob get(UUID jobId) {
        CorrelationJob job = jobs.get(jobId);
        if (job == null) throw new WorkspaceException("CORRELATION_JOB_NOT_FOUND", "Correlation job not found: " + jobId);
        return job;
    }

    private void execute(UUID jobId, WorkspaceEntity workspace) {
        jobs.computeIfPresent(jobId, (id, job) -> job.running());
        try {
            Path repository = Path.of(workspace.getRepositoryPath());
            Path reports = repository.resolve("target/scafix/reports");
            Path canonicalPath = reports.resolve("canonical-report.json");
            if (!Files.exists(canonicalPath)) throw new WorkspaceException("REPORT_NOT_IMPORTED", "Import a report before correlation");
            CanonicalScanReport report = new CanonicalReportJsonCodec().read(Files.readAllBytes(canonicalPath));
            List<ProjectTree> projectTrees = loadTrees(repository);
            CorrelationResult result = core.correlate(report, projectTrees,
                    UpdateLevel.parse(configurations.get(workspace.getId()).updateLevel()));
            Path output = reports.resolve("correlation.json");
            Files.write(output, new CorrelationJsonCodec().write(result));
            int matched = (int) result.entries().stream().filter(entry -> entry.status().name().equals("MATCHED")).count();
            int ambiguous = (int) result.entries().stream().filter(entry -> entry.status().name().equals("AMBIGUOUS")).count();
            int orphan = (int) result.entries().stream().filter(entry -> entry.status().name().equals("ORPHAN")).count();
            jobs.computeIfPresent(jobId, (id, job) -> job.completed(result.entries().size(), matched, ambiguous, orphan,
                    String.join("\n", result.warnings()), output.toString(), Instant.now()));
        } catch (Exception exception) {
            jobs.computeIfPresent(jobId, (id, job) -> job.failed(exception.getMessage(), Instant.now()));
        }
    }

    private List<ProjectTree> loadTrees(Path repository) throws java.io.IOException {
        List<ProjectTree> result = new ArrayList<>();
        Path analysis = repository.resolve("target/scafix/ui-analysis");
        if (!Files.exists(analysis)) throw new WorkspaceException("MAVEN_ANALYSIS_REQUIRED", "Run Maven analysis before correlation");
        List<ProjectModel> projects = discovery.discover(ProjectDiscoveryRequest.cli(repository, List.of("*"))).projects();
        try (var paths = Files.walk(analysis)) {
            for (Path treePath : paths.filter(path -> path.getFileName().toString().equals("dependency-tree.json")).toList()) {
                DependencyTree tree = trees.parse(treePath);
                String projectId = tree.root().coordinate().gav();
                Path pom = projects.stream().filter(project -> project.coordinate().gav().equals(projectId))
                        .map(project -> repository.resolve(project.pomPath())).findFirst().orElse(Path.of("unknown/pom.xml"));
                result.add(new ProjectTree(projectId, pom, tree));
            }
        }
        if (result.isEmpty()) throw new WorkspaceException("MAVEN_ANALYSIS_REQUIRED", "Run Maven analysis before correlation");
        return result;
    }

    private WorkspaceEntity workspace(UUID id) { return workspaces.findById(id).orElseThrow(() ->
            new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + id)); }
}
