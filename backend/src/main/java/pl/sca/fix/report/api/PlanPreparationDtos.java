package pl.sca.fix.report.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.sca.fix.report.domain.PlanPreparationRunStatus;
import pl.sca.fix.report.domain.PlanPreparationStatus;

public final class PlanPreparationDtos {
    private PlanPreparationDtos() { }
    public record PreparationRunDto(UUID runId, UUID reportId, UUID planId, PlanPreparationRunStatus status,
                                    Instant createdAt, Instant completedAt, List<PreparationItemDto> items) { }
    public record PreparationItemDto(UUID itemId, String groupId, String artifactId, String version,
                                     Double maxCvssScore, UUID jobId, UUID findingId, UUID occurrenceId,
                                     PlanPreparationStatus status, String message, Instant updatedAt) { }
}
