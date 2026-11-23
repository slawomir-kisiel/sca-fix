package pl.sca.fix.uibackend.workspace;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.port.GitClonePort;
import pl.sca.fix.core.port.GitPort;

@Service
public class GitCloneService {
    private final WorkspaceRepository workspaces;
    private final GitClonePort gitClone;
    private final GitPort git;
    private final ConcurrentMap<UUID, CloneJob> jobs = new ConcurrentHashMap<>();

    public GitCloneService(WorkspaceRepository workspaces, GitClonePort gitClone, GitPort git) {
        this.workspaces = workspaces;
        this.gitClone = gitClone;
        this.git = git;
    }

    public ClonePlan plan(String repositoryUrl, String requestedPath) {
        if (repositoryUrl == null || repositoryUrl.isBlank())
            throw new WorkspaceException("REPOSITORY_URL_REQUIRED", "Repository URL is required");
        Path path = requestedPath == null || requestedPath.isBlank()
                ? Path.of("./work").resolve(repositoryName(repositoryUrl))
                : Path.of(requestedPath.trim());
        return new ClonePlan(repositoryUrl.trim(), path.toAbsolutePath().normalize().toString());
    }

    public CloneJob start(String repositoryUrl, String requestedPath, String branch) {
        ClonePlan plan = plan(repositoryUrl, requestedPath);
        Path destination = Path.of(plan.proposedClonePath());
        if (java.nio.file.Files.exists(destination)) {
            throw new WorkspaceException("CLONE_PATH_EXISTS", "Clone destination already exists: " + destination);
        }
        UUID id = UUID.randomUUID();
        CloneJob queued = new CloneJob(id, "QUEUED", plan.repositoryUrl(), plan.proposedClonePath(), null, Instant.now(), null);
        jobs.put(id, queued);
        CompletableFuture.runAsync(() -> execute(id, plan, branch));
        return queued;
    }

    public CloneJob get(UUID id) {
        CloneJob job = jobs.get(id);
        if (job == null) throw new WorkspaceException("CLONE_JOB_NOT_FOUND", "Clone job not found: " + id);
        return job;
    }

    private void execute(UUID id, ClonePlan plan, String branch) {
        jobs.computeIfPresent(id, (key, value) -> value.withStatus("RUNNING", null, null));
        try {
            Path destination = Path.of(plan.proposedClonePath());
            gitClone.cloneRepository(plan.repositoryUrl(), destination, branch);
            var status = git.status(destination);
            Instant finished = Instant.now();
            workspaces.save(new WorkspaceEntity(UUID.randomUUID(), destination.toString(), "READY", finished, finished));
            jobs.computeIfPresent(id, (key, value) -> value.withStatus("SUCCESS", null, finished));
        } catch (RuntimeException exception) {
            jobs.computeIfPresent(id, (key, value) -> value.withStatus("FAILED", exception.getMessage(), Instant.now()));
        }
    }

    private static String repositoryName(String url) {
        String value = url.trim().replace('\\', '/');
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }

    public record ClonePlan(String repositoryUrl, String proposedClonePath) {}
}

