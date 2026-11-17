package pl.sca.fix.report.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReportAdapterRegistry {
    private final ObjectMapper objectMapper;
    private final List<ScanReportAdapter> adapters;

    public ReportAdapterRegistry(ObjectMapper objectMapper, List<ScanReportAdapter> adapters) {
        this.objectMapper = objectMapper;
        this.adapters = adapters;
    }

    public ReportDetectionResult detect(Path reportPath) {
        try {
            JsonNode document = objectMapper.readTree(reportPath.toFile());
            ScanReportAdapter adapter = adapters.stream().filter(item -> item.supports(document)).findFirst()
                    .orElseThrow(() -> new UnsupportedReportFormatException("Unsupported report format"));
            var canonical = adapter.parse(document);
            return new ReportDetectionResult(canonical.source(), canonical.format(), canonical.formatVersion(), canonical);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to parse report JSON", exception);
        }
    }

    public static class UnsupportedReportFormatException extends RuntimeException {
        public UnsupportedReportFormatException(String message) { super(message); }
    }
}
