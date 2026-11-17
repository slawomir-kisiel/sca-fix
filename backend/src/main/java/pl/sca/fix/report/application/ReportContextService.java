package pl.sca.fix.report.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.sca.fix.dependency.api.DependencyGraphDtos.DependencyAnalysisResponse;
import pl.sca.fix.dependency.application.DependencyAnalysisService;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.report.api.ReportContextDtos.UpdateReportContextRequest;
import pl.sca.fix.report.api.ReportContextDtos.UpdateReportContextResponse;
import pl.sca.fix.report.api.ReportDtos.JobReferenceDto;
import pl.sca.fix.report.domain.ReportStatus;
import pl.sca.fix.report.domain.SecurityScanReport;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;

@Service
public class ReportContextService {
    private final SecurityScanReportRepository reports;
    private final ProjectRepository projects;
    private final DependencyAnalysisService dependencyAnalysis;

    public ReportContextService(SecurityScanReportRepository reports, ProjectRepository projects,
                                DependencyAnalysisService dependencyAnalysis) {
        this.reports = reports; this.projects = projects; this.dependencyAnalysis = dependencyAnalysis;
    }

    @Transactional
    public UpdateReportContextResponse update(UUID reportId, UpdateReportContextRequest request) {
        SecurityScanReport report = reports.findById(reportId).orElseThrow(() -> new ReportNotFoundException(reportId));
        Project project = projects.findById(request.projectId()).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (!project.getRepositoryId().equals(request.repositoryId())) throw new IllegalArgumentException("Project does not belong to repository");
        if (report.getRepositoryId() != null && !report.getRepositoryId().equals(request.repositoryId())) throw new IllegalArgumentException("Report is linked to another repository");
        String pomPath = request.pomPath() == null || request.pomPath().isBlank() ? project.getPomPath() : request.pomPath();
        if (!project.getPomPath().equals(pomPath)) throw new IllegalArgumentException("pomPath does not match project");
        report.applyContext(request.repositoryId(), project.getId(), pomPath, request.branch(), request.commitSha());
        report.setStatus(ReportStatus.IN_PROGRESS);
        reports.save(report);
        DependencyAnalysisResponse job = dependencyAnalysis.start(project.getId(), reportId);
        return new UpdateReportContextResponse(reportId, request.repositoryId(), project.getId(), pomPath, request.branch(), request.commitSha(), report.getContextSource(), report.getStatus().name(), new JobReferenceDto(job.jobId(), job.status()));
    }
}
