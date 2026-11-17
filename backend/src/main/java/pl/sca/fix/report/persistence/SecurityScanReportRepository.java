package pl.sca.fix.report.persistence;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.report.domain.SecurityScanReport;

public interface SecurityScanReportRepository extends JpaRepository<SecurityScanReport, UUID> {
    List<SecurityScanReport> findByRepositoryIdOrderByImportedAtDesc(UUID repositoryId);
    long countByRepositoryId(UUID repositoryId);
}
