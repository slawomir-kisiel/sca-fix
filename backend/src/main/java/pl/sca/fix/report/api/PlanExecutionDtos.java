package pl.sca.fix.report.api;

import java.util.UUID;

public final class PlanExecutionDtos {
    private PlanExecutionDtos() { }
    public record StartExecutionRequest(boolean diffConfirmed) { }
    public record ExecutionStartedDto(UUID planId, UUID jobId, String status) { }
}
