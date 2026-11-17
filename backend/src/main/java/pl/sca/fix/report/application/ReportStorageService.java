package pl.sca.fix.report.application;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.sca.fix.report.api.ReportDtos.ReportImportResponseDto;
import pl.sca.fix.report.api.ReportDtos.SecurityScanReportMetadataDto;
import pl.sca.fix.report.domain.SecurityScanReport;
import pl.sca.fix.report.domain.ReportStatus;
import pl.sca.fix.report.integration.ReportAdapterRegistry;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;

@Service
public class ReportStorageService {
    private static final Logger log = LoggerFactory.getLogger(ReportStorageService.class);
    private final SecurityScanReportRepository reports;
    private final Path reportsDirectory;
    private final ReportAdapterRegistry adapters;
    private final VulnerabilityFindingRepository findings;

    public ReportStorageService(SecurityScanReportRepository reports,
                                @org.springframework.beans.factory.annotation.Value("${sca.reports.root:./data/reports}") String reportsRoot,
                                ReportAdapterRegistry adapters, VulnerabilityFindingRepository findings) {
        this.reports = reports;
        this.reportsDirectory = Path.of(reportsRoot).toAbsolutePath().normalize();
        this.adapters = adapters;
        this.findings = findings;
    }

    @Transactional
    public ReportImportResponseDto store(MultipartFile file, UUID repositoryId) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Report file must not be empty");
        try {
            UUID id = UUID.randomUUID();
            Files.createDirectories(reportsDirectory);
            Path reportPath = reportsDirectory.resolve(id + ".json");
            long sizeBytes;
            String sha256;
            try (InputStream input = file.getInputStream()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (var output = Files.newOutputStream(reportPath)) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        total += read;
                    }
                    sizeBytes = total;
                }
                sha256 = HexFormat.of().formatHex(digest.digest());
            }
            var detection = detectOrKeepRaw(reportPath);
            SecurityScanReport report = new SecurityScanReport(id, repositoryId,
                    file.getOriginalFilename() == null ? "report" : file.getOriginalFilename(),
                    file.getContentType(), sizeBytes, sha256, Instant.now(), detection.source(), detection.format(), detection.formatVersion());
            try {
                reports.save(report);
                if (detection.canonicalReport() == null) {
                    report.setStatus(ReportStatus.REQUIRES_REGISTRATION);
                } else {
                    report.setStatus(ReportStatus.IN_PROGRESS);
                    detection.canonicalReport().findings().stream()
                            .map(item -> new pl.sca.fix.report.domain.VulnerabilityFinding(id, item))
                            .forEach(findings::save);
                    report.setStatus(ReportStatus.READY);
                }
                reports.save(report);
            } catch (RuntimeException exception) {
                Files.deleteIfExists(reportPath);
                throw exception;
            }
            log.info("Stored security scan report metadata and file reportId={} repositoryId={} filename={} sizeBytes={} sha256={} path={}", id, repositoryId, report.getOriginalFilename(), sizeBytes, report.getSha256(), reportPath);
            return new ReportImportResponseDto(id, report.getStatus().name(), report.getSource(), report.getFormat(), report.getFormatVersion(), null);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException("Unable to read report file", exception);
        }
    }

    @Transactional(readOnly = true)
    public SecurityScanReportMetadataDto metadata(UUID reportId) { return metadata(reports.findById(reportId).orElseThrow(() -> new ReportNotFoundException(reportId))); }

    @Transactional(readOnly = true)
    public byte[] raw(UUID reportId) {
        if (!reports.existsById(reportId)) throw new ReportNotFoundException(reportId);
        try {
            return Files.readAllBytes(reportPath(reportId));
        } catch (IOException exception) {
            throw new ReportNotFoundException(reportId);
        }
    }

    private SecurityScanReportMetadataDto metadata(SecurityScanReport report) {
        return new SecurityScanReportMetadataDto(report.getId(), report.getRepositoryId(), report.getOriginalFilename(), report.getContentType(), report.getSource(), report.getFormat(), report.getFormatVersion(), report.getImportedAt(), report.getScanDate(), report.getSizeBytes(), report.getSha256(), report.getStatus().name());
    }

    private Path reportPath(UUID reportId) {
        return reportsDirectory.resolve(reportId + ".json");
    }

    private pl.sca.fix.report.integration.ReportDetectionResult detectOrKeepRaw(Path reportPath) {
        try {
            return adapters.detect(reportPath);
        } catch (ReportAdapterRegistry.UnsupportedReportFormatException exception) {
            log.warn("No adapter found for security scan report path={}; keeping original content", reportPath);
            return new pl.sca.fix.report.integration.ReportDetectionResult("UNKNOWN", "JSON", null, null);
        }
    }
}
