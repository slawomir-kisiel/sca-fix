package pl.sca.fix.repository.application;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.sca.fix.repository.api.RepositoryDtos.RepositoryDetailsDto;
import pl.sca.fix.repository.api.RepositoryDtos.RepositorySummaryDto;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.report.api.ReportDtos.SecurityScanReportMetadataDto;
import pl.sca.fix.report.domain.SecurityScanReport;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;

@Service
public class RepositoryQueryService {
    private final RepositoryRepository repositories;
    private final ProjectRepository projects;
    private final SecurityScanReportRepository reports;

    public RepositoryQueryService(RepositoryRepository repositories, ProjectRepository projects,
                                  SecurityScanReportRepository reports) {
        this.repositories = repositories; this.projects = projects; this.reports = reports;
    }

    @Transactional(readOnly = true)
    public List<RepositorySummaryDto> list() {
        return repositories.findAll().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public RepositoryDetailsDto details(UUID id) {
        Repository repository = repositories.findById(id)
                .orElseThrow(() -> new RepositoryNotFoundException(id));
        List<SecurityScanReportMetadataDto> reportMetadata = reports.findByRepositoryIdOrderByImportedAtDesc(id)
                .stream().map(this::reportMetadata).toList();
        return new RepositoryDetailsDto(summary(repository), List.of(), List.of(), reportMetadata, List.of(), null);
    }

    public RepositorySummaryDto summary(Repository repository) {
        String name = Path.of(repository.getClonePath()).getFileName().toString();
        return new RepositorySummaryDto(repository.getId(), name, repository.getUrl(), repository.getClonePath(),
                repository.getDefaultBranch(), repository.getBaseCommitSha(), projects.findByRepositoryIdOrderByPomPath(repository.getId()).size(), reports.countByRepositoryId(repository.getId()), 0,
                repository.getStatus().name());
    }

    private SecurityScanReportMetadataDto reportMetadata(SecurityScanReport report) {
        return new SecurityScanReportMetadataDto(report.getId(), report.getRepositoryId(), report.getOriginalFilename(),
                report.getContentType(), report.getSource(), report.getFormat(), report.getFormatVersion(),
                report.getImportedAt(), report.getScanDate(), report.getSizeBytes(), report.getSha256(), report.getStatus().name());
    }
}
