package pl.sca.fix.uibackend.workspace;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.discovery.ProjectDiscoveryService;
import pl.sca.fix.core.model.ProjectModel;
import pl.sca.fix.core.port.MavenDependencyTreeRequest;
import pl.sca.fix.core.port.MavenEffectivePomRequest;
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.uibackend.config.WorkspaceConfigurationService;

@Service
public class MavenAnalysisService {
    private final ProjectDiscoveryService discovery;
    private final WorkspaceRepository workspaces;
    private final MavenPort maven;
    private final WorkspaceConfigurationService configurations;
    private final ConcurrentMap<UUID, MavenAnalysisJob> jobs = new ConcurrentHashMap<>();

    public MavenAnalysisService(ProjectDiscoveryService discovery,
                                WorkspaceRepository workspaces, MavenPort maven,
                                WorkspaceConfigurationService configurations) {
        this.discovery = discovery;
        this.workspaces = workspaces;
        this.maven = maven;
        this.configurations = configurations;
    }

    public List<MavenAnalysisJob> start(UUID workspaceId, List<String> requestedProjects) {
        WorkspaceEntity workspace = workspaces.findById(workspaceId).orElseThrow(() ->
                new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId));
        List<String> selected = requestedProjects == null || requestedProjects.isEmpty()
                ? configurations.get(workspaceId).analysisProjects() : requestedProjects;
        List<ProjectModel> available = discovery.discover(pl.sca.fix.core.discovery.ProjectDiscoveryRequest.cli(
                Path.of(workspace.getRepositoryPath()), List.of("*"))).projects();
        List<ProjectModel> toAnalyze = available.stream().filter(project -> selected.contains("*")
                || selected.contains(project.coordinate().groupId() + ":" + project.coordinate().artifactId())).toList();
        if (toAnalyze.isEmpty()) throw new WorkspaceException("NO_PROJECTS_SELECTED", "No projects selected for Maven analysis");
        return toAnalyze.stream().map(project -> queue(workspaceId, workspace, project)).toList();
    }

    public MavenAnalysisJob get(UUID jobId) {
        MavenAnalysisJob job = jobs.get(jobId);
        if (job == null) throw new WorkspaceException("MAVEN_JOB_NOT_FOUND", "Maven analysis job not found: " + jobId);
        return job;
    }

    private MavenAnalysisJob queue(UUID workspaceId, WorkspaceEntity workspace, ProjectModel project) {
        UUID jobId = UUID.randomUUID();
        Path pom = Path.of(workspace.getRepositoryPath()).resolve(project.pomPath()).normalize();
        Path output = Path.of(workspace.getRepositoryPath()).resolve("target/scafix/ui-analysis")
                .resolve(project.coordinate().artifactId() + "-" + jobId);
        Path effective = output.resolve("effective-pom.xml");
        Path tree = output.resolve("dependency-tree.json");
        String command = "mvn -N -f " + pom + " org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom"
                + " && mvn -N -f " + pom + " org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree";
        MavenAnalysisJob queued = new MavenAnalysisJob(jobId, workspaceId, project.coordinate().gav(),
                project.pomPath().toString().replace('\\', '/'), "QUEUED", command, "", "", null, Instant.now(), null, null);
        jobs.put(jobId, queued);
        CompletableFuture.runAsync(() -> execute(jobId, pom, effective, tree));
        return queued;
    }

    private void execute(UUID jobId, Path pom, Path effective, Path tree) {
        jobs.computeIfPresent(jobId, (id, job) -> job.withRunning(Instant.now()));
        try {
            var effectiveResult = maven.generateEffectivePom(new MavenEffectivePomRequest(pom, effective));
            var treeResult = maven.generateDependencyTree(new MavenDependencyTreeRequest(pom, tree, null, false));
            var stdout = effectiveResult.processResult().stdout() + treeResult.processResult().stdout();
            var stderr = effectiveResult.processResult().stderr() + treeResult.processResult().stderr();
            jobs.computeIfPresent(jobId, (id, job) -> job.withFinished("COMPLETED", stdout, stderr, null, Instant.now()));
        } catch (RuntimeException exception) {
            jobs.computeIfPresent(jobId, (id, job) -> job.withFinished("FAILED", "", "", exception.getMessage(), Instant.now()));
        }
    }
}
