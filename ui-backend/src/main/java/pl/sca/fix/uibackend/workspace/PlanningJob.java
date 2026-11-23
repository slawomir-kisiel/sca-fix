package pl.sca.fix.uibackend.workspace;

import java.time.Instant;
import java.util.UUID;

public record PlanningJob(UUID jobId, UUID workspaceId, String status, int planned, int skipped,
                          String errorMessage, String outputPath, Instant createdAt, Instant finishedAt) {
    PlanningJob running() { return new PlanningJob(jobId, workspaceId, "PLANNING", planned, skipped, null, outputPath, createdAt, null); }
    PlanningJob completed(int planned, int skipped, String output, Instant finished) { return new PlanningJob(jobId, workspaceId, "PLANNED", planned, skipped, null, output, createdAt, finished); }
    PlanningJob failed(String error, Instant finished) { return new PlanningJob(jobId, workspaceId, "FAILED", planned, skipped, error, outputPath, createdAt, finished); }
}
