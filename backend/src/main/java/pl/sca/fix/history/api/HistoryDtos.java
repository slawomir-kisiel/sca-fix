package pl.sca.fix.history.api;

import java.time.Instant;
import java.util.List;

public final class HistoryDtos {
    private HistoryDtos() { }

    public record HistoryEntryDto(
            String entryId,
            String entryType,
            Instant occurredAt,
            Instant finishedAt,
            String status,
            String repositoryId,
            String repositoryName,
            String projectName,
            String jobId,
            String planId,
            String reportId,
            String taskId,
            String jobType,
            String branchName,
            String baseCommitSha,
            String message,
            String targetHash) { }

    public record HistoryPageDto(List<HistoryEntryDto> entries, long total, int page, int size, boolean hasNext) { }

    public record DashboardDto(long repositories, long reports, long openFindings,
                               long activeJobs, long plannedPlans, List<HistoryEntryDto> recentActivity) { }
}
