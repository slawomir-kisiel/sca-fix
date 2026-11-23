package pl.sca.fix.uibackend.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GitCommitJob(UUID jobId, UUID workspaceId, String status, String commitSha,
                           String branch, List<String> changedFiles, boolean pushed,
                           String errorMessage, Instant createdAt, Instant finishedAt) {
    GitCommitJob running() { return new GitCommitJob(jobId, workspaceId, "COMMITTING", null, branch, changedFiles, false, null, createdAt, null); }
    GitCommitJob completed(String sha, boolean pushed, Instant finished) { return new GitCommitJob(jobId, workspaceId, "COMPLETED", sha, branch, changedFiles, pushed, null, createdAt, finished); }
    GitCommitJob failed(String error, Instant finished) { return new GitCommitJob(jobId, workspaceId, "FAILED", null, branch, changedFiles, false, error, createdAt, finished); }
}
