package pl.sca.fix.report.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.sca.fix.report.domain.PlanItemStatus;
import pl.sca.fix.report.domain.UpdatePlanStatus;
import pl.sca.fix.report.domain.UpdateStrategy;

public final class UpdatePlanDtos {
    private UpdatePlanDtos() { }
    public record CreatePlanRequest(String name, UUID repositoryId) { }
    public record ChangePlanStatusRequest(UpdatePlanStatus status) { }
    public record PlanSummaryDto(UUID planId, UUID repositoryId, String taskId, String name, UpdatePlanStatus status,
                                 boolean active, Instant createdAt, Instant updatedAt, long itemCount,
                                 String branchName, String baseCommitSha) { }
    public record PlanItemDto(UUID itemId, UUID updatePlanId, UUID findingId, UUID occurrenceId,
                              UUID targetProjectId, UUID changePointTreeNodeId,
                              UUID targetArtifactVersionId, String currentVersion,
                              String targetVersion, UpdateStrategy strategy,
                              PlanItemStatus status, String appliedFromVersion, String errorMessage, Instant selectedAt) { }
    public record PlanDetailsDto(PlanSummaryDto plan, List<PlanItemDto> items,
                                 List<String> warnings) { }
}
