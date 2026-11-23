package pl.sca.fix.uibackend.workspace;

import java.time.Instant;
import java.util.UUID;

public record MavenAnalysisJob(UUID jobId, UUID workspaceId, String projectCoordinate, String pomPath,
                               String status, String command, String stdout, String stderr,
                               String errorMessage, Instant createdAt, Instant startedAt, Instant finishedAt) {
    MavenAnalysisJob withRunning(Instant started) {
        return new MavenAnalysisJob(jobId, workspaceId, projectCoordinate, pomPath, "RUNNING", command,
                stdout, stderr, null, createdAt, started, null);
    }
    MavenAnalysisJob withFinished(String newStatus, String newStdout, String newStderr, String error, Instant finished) {
        return new MavenAnalysisJob(jobId, workspaceId, projectCoordinate, pomPath, newStatus, command,
                newStdout, newStderr, error, createdAt, startedAt, finished);
    }
}
