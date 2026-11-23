package pl.sca.fix.uibackend.workspace;

import java.time.Instant;
import java.util.UUID;

public record CorrelationJob(UUID jobId, UUID workspaceId, String status, int projects,
                             int libraries, int matched, int ambiguous, int orphan,
                             String warnings, String errors, String outputPath,
                             Instant createdAt, Instant finishedAt) {
    CorrelationJob running() { return new CorrelationJob(jobId, workspaceId, "RUNNING", projects, libraries, matched, ambiguous, orphan, warnings, errors, outputPath, createdAt, null); }
    CorrelationJob completed(int libraries, int matched, int ambiguous, int orphan, String warnings, String outputPath, Instant finished) {
        return new CorrelationJob(jobId, workspaceId, "COMPLETED", projects, libraries, matched, ambiguous, orphan, warnings, "", outputPath, createdAt, finished);
    }
    CorrelationJob failed(String error, Instant finished) { return new CorrelationJob(jobId, workspaceId, "FAILED", projects, libraries, matched, ambiguous, orphan, warnings, error, outputPath, createdAt, finished); }
}
