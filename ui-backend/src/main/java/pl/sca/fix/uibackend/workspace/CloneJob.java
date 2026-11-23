package pl.sca.fix.uibackend.workspace;
import java.time.Instant;
import java.util.UUID;
public record CloneJob(UUID jobId, String status, String repositoryUrl, String clonePath,
                       String errorMessage, Instant createdAt, Instant finishedAt) {
    CloneJob withStatus(String value, String error, Instant finished) {
        return new CloneJob(jobId, value, repositoryUrl, clonePath, error, createdAt, finished);
    }
}

