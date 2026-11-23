package pl.sca.fix.uibackend.workspace;

import java.time.Instant;
import java.util.UUID;

public record PlanApplyJob(UUID jobId, UUID workspaceId, String planId, String status,
                           int selected, int applied, int failed, int skipped,
                           String errorMessage, Instant createdAt, Instant finishedAt) {
    PlanApplyJob running() { return new PlanApplyJob(jobId, workspaceId, planId, "APPLYING", selected, 0, 0, 0, null, createdAt, null); }
    PlanApplyJob completed(int applied, int failed, int skipped, String error, Instant finished) {
        return new PlanApplyJob(jobId, workspaceId, planId, failed == 0 ? "APPLIED" : "FAILED", selected, applied, failed, skipped, error, createdAt, finished);
    }
    PlanApplyJob failed(String error, Instant finished) { return new PlanApplyJob(jobId, workspaceId, planId, "FAILED", selected, 0, 0, 0, error, createdAt, finished); }
}
