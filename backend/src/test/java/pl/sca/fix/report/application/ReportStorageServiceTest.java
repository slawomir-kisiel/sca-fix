package pl.sca.fix.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import pl.sca.fix.report.domain.SecurityScanReport;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;
import pl.sca.fix.report.integration.ReportAdapterRegistry;
import pl.sca.fix.report.integration.ReportDetectionResult;

class ReportStorageServiceTest {

    @TempDir Path reportsDirectory;

    @Test
    void storesOriginalBytesAndSha256() throws Exception {
        SecurityScanReportRepository reports = Mockito.mock(SecurityScanReportRepository.class);
        ReportAdapterRegistry adapters = Mockito.mock(ReportAdapterRegistry.class);
        Mockito.when(adapters.detect(any())).thenReturn(new ReportDetectionResult("TEST", "JSON", "1", null));
        VulnerabilityFindingRepository findings = Mockito.mock(VulnerabilityFindingRepository.class);
        ReportStorageService service = new ReportStorageService(reports, reportsDirectory.toString(), adapters, findings);
        byte[] bytes = "{\"scan\":true}".getBytes(StandardCharsets.UTF_8);

        var response = service.store(new MockMultipartFile("file", "scan.json", "application/json", bytes), null);

        assertThat(response.reportId()).isNotNull();
        assertThat(response.status()).isEqualTo("REQUIRES_REGISTRATION");
        ArgumentCaptor<SecurityScanReport> reportCaptor = ArgumentCaptor.forClass(SecurityScanReport.class);
        verify(reports, Mockito.times(2)).save(reportCaptor.capture());
        var savedReport = reportCaptor.getAllValues().getLast();
        assertThat(savedReport.getSha256()).isEqualTo("9151d2989617bca6d02f64528c72dc5dd8ed57324edc22d4d6ff29dbb18eb64c");
        assertThat(Files.readAllBytes(reportsDirectory.resolve(savedReport.getId() + ".json"))).containsExactly(bytes);
    }
}
