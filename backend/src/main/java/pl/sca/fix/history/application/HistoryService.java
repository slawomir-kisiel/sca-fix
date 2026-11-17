package pl.sca.fix.history.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.sca.fix.history.api.HistoryDtos.DashboardDto;
import pl.sca.fix.history.api.HistoryDtos.HistoryEntryDto;
import pl.sca.fix.history.api.HistoryDtos.HistoryPageDto;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.job.persistence.JobRepository;
import pl.sca.fix.report.domain.FindingStatus;
import pl.sca.fix.report.domain.SecurityScanReport;
import pl.sca.fix.report.domain.UpdatePlan;
import pl.sca.fix.report.domain.UpdatePlanStatus;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;
import pl.sca.fix.report.persistence.UpdatePlanRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;

@Service
public class HistoryService {
    private final JobRepository jobs;
    private final UpdatePlanRepository plans;
    private final SecurityScanReportRepository reports;
    private final VulnerabilityFindingRepository findings;
    private final RepositoryRepository repositories;

    public HistoryService(JobRepository jobs, UpdatePlanRepository plans,
                          SecurityScanReportRepository reports,
                          VulnerabilityFindingRepository findings,
                          RepositoryRepository repositories) {
        this.jobs = jobs;
        this.plans = plans;
        this.reports = reports;
        this.findings = findings;
        this.repositories = repositories;
    }

    @Transactional(readOnly = true)
    public HistoryPageDto history(String query, List<String> types, List<String> statuses,
                                  UUID repositoryId, LocalDate from, LocalDate to,
                                  int page, int size) {
        Map<UUID, Repository> repositoryMap = repositories.findAll().stream()
                .collect(Collectors.toMap(Repository::getId, item -> item));
        List<HistoryEntryDto> entries = allEntries(repositoryMap).stream()
                .filter(matches(query, types, statuses, repositoryId, from, to))
                .sorted(Comparator.comparing(HistoryEntryDto::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        int start = Math.min(safePage * safeSize, entries.size());
        int end = Math.min(start + safeSize, entries.size());
        return new HistoryPageDto(entries.subList(start, end), entries.size(), safePage, safeSize, end < entries.size());
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        Map<UUID, Repository> repositoryMap = repositories.findAll().stream()
                .collect(Collectors.toMap(Repository::getId, item -> item));
        List<HistoryEntryDto> recent = allEntries(repositoryMap).stream()
                .sorted(Comparator.comparing(HistoryEntryDto::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
        long openFindings = findings.countByStatusIn(List.of(FindingStatus.PENDING, FindingStatus.AMBIGUOUS, FindingStatus.ORPHAN));
        long activeJobs = jobs.countByStatusIn(List.of(JobStatus.QUEUED, JobStatus.RUNNING));
        return new DashboardDto(repositories.count(), reports.count(), openFindings, activeJobs,
                plans.countByStatus(UpdatePlanStatus.PLANNED), recent);
    }

    private List<HistoryEntryDto> allEntries(Map<UUID, Repository> repositoryMap) {
        List<HistoryEntryDto> result = new ArrayList<>();
        jobs.findAll().forEach(job -> result.add(jobEntry(job)));
        plans.findAll().forEach(plan -> result.add(planEntry(plan, repositoryMap)));
        reports.findAll().forEach(report -> result.add(reportEntry(report, repositoryMap)));
        return result;
    }

    private HistoryEntryDto jobEntry(Job job) {
        String message = job.getErrorMessage() == null ? null : job.getErrorMessage();
        return new HistoryEntryDto(job.getId().toString(), "JOB", job.getCreatedAt(), job.getFinishedAt(),
                job.getStatus().name(), null, null, null, job.getId().toString(), null, null, null,
                job.getJobType(), null, null, message, "#jobs/" + job.getId());
    }

    private HistoryEntryDto planEntry(UpdatePlan plan, Map<UUID, Repository> repositoryMap) {
        Repository repository = plan.getRepositoryId() == null ? null : repositoryMap.get(plan.getRepositoryId());
        return new HistoryEntryDto(plan.getId().toString(), "PLAN", plan.getCreatedAt(),
                plan.getCommittedAt() != null ? plan.getCommittedAt() : plan.getAppliedAt(),
                plan.getStatus().name(), id(plan.getRepositoryId()), repositoryName(repository), null,
                null, plan.getId().toString(), null, plan.getTaskId(), null, plan.getBranchName(),
                plan.getBaseCommitSha(), null, "#plans/" + plan.getId());
    }

    private HistoryEntryDto reportEntry(SecurityScanReport report, Map<UUID, Repository> repositoryMap) {
        Repository repository = report.getRepositoryId() == null ? null : repositoryMap.get(report.getRepositoryId());
        return new HistoryEntryDto(report.getId().toString(), "REPORT", report.getImportedAt(), null,
                report.getStatus().name(), id(report.getRepositoryId()), repositoryName(repository), null,
                null, null, report.getId().toString(), null, null, null, null,
                report.getSource() == null ? report.getOriginalFilename() : report.getSource(),
                "#report/" + report.getId());
    }

    private Predicate<HistoryEntryDto> matches(String query, List<String> types, List<String> statuses,
                                                UUID repositoryId, LocalDate from, LocalDate to) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> normalizedTypes = normalize(types);
        List<String> normalizedStatuses = normalize(statuses);
        Instant fromInstant = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return entry -> (normalizedQuery.isBlank() || searchable(entry).contains(normalizedQuery))
                && (normalizedTypes.isEmpty() || normalizedTypes.contains(entry.entryType()))
                && (normalizedStatuses.isEmpty() || normalizedStatuses.contains(entry.status()))
                && (repositoryId == null || repositoryId.toString().equals(entry.repositoryId()))
                && (fromInstant == null || !entry.occurredAt().isBefore(fromInstant))
                && (toInstant == null || entry.occurredAt().isBefore(toInstant));
    }

    private String searchable(HistoryEntryDto entry) {
        return String.join(" ", nullSafe(entry.entryId()), nullSafe(entry.entryType()), nullSafe(entry.status()),
                nullSafe(entry.repositoryName()), nullSafe(entry.projectName()), nullSafe(entry.jobId()),
                nullSafe(entry.planId()), nullSafe(entry.reportId()), nullSafe(entry.taskId()),
                nullSafe(entry.jobType()), nullSafe(entry.branchName()), nullSafe(entry.message())).toLowerCase(Locale.ROOT);
    }

    private List<String> normalize(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT)).toList();
    }

    private String id(UUID value) { return value == null ? null : value.toString(); }
    private String nullSafe(String value) { return value == null ? "" : value; }
    private String repositoryName(Repository repository) { return repository == null ? null : repository.getUrl() != null ? repository.getUrl() : repository.getClonePath(); }
}
