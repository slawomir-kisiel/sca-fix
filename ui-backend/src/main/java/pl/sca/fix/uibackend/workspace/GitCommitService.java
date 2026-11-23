package pl.sca.fix.uibackend.workspace;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.plan.PlanDocument;
import pl.sca.fix.core.plan.PlanDocumentJsonCodec;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitStatus;
import pl.sca.fix.uibackend.config.WorkspaceConfiguration;
import pl.sca.fix.uibackend.config.WorkspaceConfigurationService;

@Service
public class GitCommitService {
    private final WorkspaceRepository workspaces;
    private final GitPort git;
    private final WorkspaceConfigurationService configurations;
    private final PlanDocumentJsonCodec codec = new PlanDocumentJsonCodec();
    private final Map<UUID, GitCommitJob> jobs = new ConcurrentHashMap<>();

    public GitCommitService(WorkspaceRepository workspaces, GitPort git, WorkspaceConfigurationService configurations) {
        this.workspaces = workspaces; this.git = git; this.configurations = configurations;
    }
    public GitStatusView status(UUID workspaceId) {
        WorkspaceEntity workspace = workspace(workspaceId);
        return GitStatusView.from(git.status(Path.of(workspace.getRepositoryPath())));
    }
    public GitCommitJob start(UUID workspaceId, String planId, String message, boolean push) {
        WorkspaceEntity workspace = workspace(workspaceId);
        if (message == null || message.isBlank()) throw new WorkspaceException("COMMIT_MESSAGE_REQUIRED", "Commit message is required");
        PlanDocument plan = readPlan(workspace);
        if (!plan.planId().equals(planId)) throw new WorkspaceException("PLAN_NOT_FOUND", "Plan not found: " + planId);
        if (!"APPLIED".equalsIgnoreCase(plan.status())) throw new WorkspaceException("PLAN_NOT_APPLIED", "Only an APPLIED plan can be committed");
        WorkspaceConfiguration configuration = configurations.get(workspaceId);
        if (push && configuration.ignoreLocalChanges()) throw new WorkspaceException("PUSH_BLOCKED", "Push is blocked when ignoreLocalChanges is enabled");
        GitStatus status = git.status(Path.of(workspace.getRepositoryPath()));
        if (status == null || !status.repository()) throw new WorkspaceException("NOT_A_GIT_REPOSITORY", "Workspace is not a Git repository");
        if (status.clean()) throw new WorkspaceException("NO_LOCAL_CHANGES", "There are no local changes to commit");
        if (push && (status.branch() == null || status.branch().isBlank())) throw new WorkspaceException("PUSH_BRANCH_REQUIRED", "Push requires a named branch");
        UUID jobId = UUID.randomUUID();
        GitCommitJob job = new GitCommitJob(jobId, workspaceId, "QUEUED", null, status.branch(), status.changedFiles().stream().map(Path::toString).toList(), false, null, Instant.now(), null);
        jobs.put(jobId, job);
        CompletableFuture.runAsync(() -> execute(jobId, workspace, message, push));
        return job;
    }
    public GitCommitJob get(UUID workspaceId, UUID jobId) {
        GitCommitJob job = jobs.get(jobId);
        if (job == null || !job.workspaceId().equals(workspaceId)) throw new WorkspaceException("JOB_NOT_FOUND", "Commit job not found: " + jobId);
        return job;
    }
    private void execute(UUID jobId, WorkspaceEntity workspace, String message, boolean push) {
        jobs.computeIfPresent(jobId, (id, job) -> job.running());
        try {
            Path repository = Path.of(workspace.getRepositoryPath()); GitStatus status = git.status(repository);
            git.add(repository, status.changedFiles()); String sha = git.commit(repository, message);
            if (push) git.push(repository, status.branch());
            jobs.computeIfPresent(jobId, (id, job) -> job.completed(sha, push, Instant.now()));
        } catch (Exception exception) { jobs.computeIfPresent(jobId, (id, job) -> job.failed(exception.getMessage(), Instant.now())); }
    }
    private PlanDocument readPlan(WorkspaceEntity workspace) { try { return codec.read(java.nio.file.Files.readAllBytes(Path.of(workspace.getRepositoryPath()).resolve("target/scafix/reports/plan.json"))); } catch (Exception exception) { throw new WorkspaceException("PLAN_READ_FAILED", exception.getMessage()); } }
    private WorkspaceEntity workspace(UUID id) { return workspaces.findById(id).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + id)); }
    public record GitStatusView(boolean repository, boolean clean, String commitSha, String branch, List<String> changedFiles) {
        static GitStatusView from(GitStatus status) { return new GitStatusView(status.repository(), status.clean(), status.commitSha(), status.branch(), status.changedFiles().stream().map(Path::toString).toList()); }
    }
}
