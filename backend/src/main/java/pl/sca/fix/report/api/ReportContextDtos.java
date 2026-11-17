package pl.sca.fix.report.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import pl.sca.fix.report.api.ReportDtos.JobReferenceDto;

public final class ReportContextDtos {
    private ReportContextDtos() { }

    public record UpdateReportContextRequest(@NotNull UUID repositoryId, @NotNull UUID projectId,
                                             String pomPath, String branch, String commitSha) { }

    public record UpdateReportContextResponse(UUID reportId, UUID repositoryId, UUID projectId,
                                              String pomPath, String branch, String commitSha,
                                              String contextSource, String status, JobReferenceDto job) { }
}
