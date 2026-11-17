package pl.sca.fix.report.application;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.dependency.domain.ArtifactTreeNode;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.report.api.ReportDtos.CorrelationResponseDto;
import pl.sca.fix.report.api.ReportDtos.JobReferenceDto;
import pl.sca.fix.report.domain.DependencyTreeOccurrence;
import pl.sca.fix.report.domain.FindingStatus;
import pl.sca.fix.report.domain.ReportStatus;
import pl.sca.fix.report.domain.SecurityScanReport;
import pl.sca.fix.report.domain.VulnerabilityFinding;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;

@Service
public class ReportCorrelationService {
    private static final Logger log = LoggerFactory.getLogger(ReportCorrelationService.class);
    private final SecurityScanReportRepository reports;
    private final VulnerabilityFindingRepository findings;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final ArtifactRepository artifacts;
    private final ArtifactVersionRepository versions;
    private final ArtifactTreeNodeRepository nodes;
    private final ProjectRepository projects;
    private final JobService jobs;
    private final TransactionTemplate transactionTemplate;

    public ReportCorrelationService(SecurityScanReportRepository reports, VulnerabilityFindingRepository findings,
                                    DependencyTreeOccurrenceRepository occurrences, ArtifactRepository artifacts,
                                    ArtifactVersionRepository versions, ArtifactTreeNodeRepository nodes,
                                    ProjectRepository projects, JobService jobs, TransactionTemplate transactionTemplate) {
        this.reports = reports; this.findings = findings; this.occurrences = occurrences; this.artifacts = artifacts;
        this.versions = versions; this.nodes = nodes; this.projects = projects; this.jobs = jobs;
        this.transactionTemplate = transactionTemplate;
    }

    public CorrelationResponseDto start(UUID reportId) {
        SecurityScanReport report = reports.findById(reportId).orElseThrow(() -> new ReportNotFoundException(reportId));
        Job job = jobs.create("REPORT_CORRELATION");
        report.setStatus(ReportStatus.IN_PROGRESS); reports.save(report);
        CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(ignored -> correlate(job.getId(), reportId)));
        return new CorrelationResponseDto(reportId, ReportStatus.IN_PROGRESS.name(), 0, 0, 0,
                new JobReferenceDto(job.getId(), job.getStatus().name()));
    }

    @Transactional
    void correlate(UUID jobId, UUID reportId) {
        try {
            jobs.start(jobId, "CORRELATION");
            UUID repositoryId = reports.findById(reportId).map(SecurityScanReport::getRepositoryId).orElse(null);
            long matched = 0, ambiguous = 0, orphan = 0;
            for (VulnerabilityFinding finding : findings.findByReportId(reportId)) {
                occurrences.deleteByFindingId(finding.getId());
                Set<OccurrenceCandidate> candidates = new HashSet<>();
                for (var affected : finding.getAffectedArtifacts()) {
                    if (affected.getGroupId() == null || affected.getArtifactId() == null) continue;
                    artifacts.findByGroupIdAndArtifactId(affected.getGroupId(), affected.getArtifactId()).ifPresent(artifact -> {
                        var matchedVersions = affected.getReportedVersion() == null
                                ? versions.findByArtifactId(artifact.getId())
                                : versions.findByArtifactIdAndVersion(artifact.getId(), affected.getReportedVersion()).stream().toList();
                        nodes.findByArtifactVersionIdIn(matchedVersions.stream().map(v -> v.getId()).toList()).stream()
                                .filter(node -> repositoryId == null || projects.findById(node.getProjectId())
                                        .map(project -> repositoryId.equals(project.getRepositoryId())).orElse(false))
                                .forEach(node -> candidates.add(new OccurrenceCandidate(affected.getId(), node)));
                    });
                }
                FindingStatus status = candidates.isEmpty() ? FindingStatus.ORPHAN : candidates.size() == 1 ? FindingStatus.MATCHED : FindingStatus.AMBIGUOUS;
                finding.setStatus(status); findings.save(finding);
                candidates.forEach(candidate -> occurrences.save(new DependencyTreeOccurrence(finding.getId(), candidate.affectedArtifactId(),
                        candidate.node().getProjectId(), candidate.node().getId(), versions.findById(candidate.node().getArtifactVersionId()).map(item -> item.getVersion()).orElse(null),
                        candidate.node().getRelationType().name(), true)));
                if (status == FindingStatus.MATCHED) matched++; else if (status == FindingStatus.AMBIGUOUS) ambiguous++; else orphan++;
            }
            boolean partial = orphan != 0;
            reports.findById(reportId).ifPresent(report -> { report.setStatus(partial ? ReportStatus.PARTIAL : ReportStatus.READY); reports.save(report); });
            jobs.finish(jobId, JobStatus.SUCCESS, 0, null, null);
            log.info("Correlated report reportId={} matched={} ambiguous={} orphan={}", reportId, matched, ambiguous, orphan);
        } catch (Exception exception) {
            reports.findById(reportId).ifPresent(report -> { report.setStatus(ReportStatus.FAILED); reports.save(report); });
            jobs.finish(jobId, JobStatus.FAILED, null, "REPORT_CORRELATION_FAILED", exception.getMessage());
            log.error("Report correlation failed reportId={} jobId={}", reportId, jobId, exception);
        }
    }

    private record OccurrenceCandidate(UUID affectedArtifactId, ArtifactTreeNode node) { }
}
