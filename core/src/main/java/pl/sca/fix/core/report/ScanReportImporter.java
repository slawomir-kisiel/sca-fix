package pl.sca.fix.core.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import pl.sca.fix.core.port.ReportDocument;

public final class ScanReportImporter {
    private final ObjectMapper mapper;
    private final List<ScanReportAdapter> adapters;
    private final ScanReportFormatDetector detector;

    public ScanReportImporter() {
        this(new ObjectMapper().findAndRegisterModules(), List.of(new OwaspDependencyCheckAdapter(),
                new CheckmarxJsonAdapter()));
    }

    public ScanReportImporter(ObjectMapper mapper, List<ScanReportAdapter> adapters) {
        this.mapper = mapper;
        this.adapters = List.copyOf(adapters);
        this.detector = new ScanReportFormatDetector(this.adapters);
    }

    public ReportImportResult importReport(ReportDocument source) {
        try {
            JsonNode root = mapper.readTree(source.content());
            if (root == null || !root.isObject()) return invalid("Report root must be a JSON object");
            ScanReportAdapter adapter = detector.detect(root, source.formatHint());
            if (adapter == null) {
                return unsupported("Unsupported report format: " + source.sourceFile().getFileName());
            }
            CanonicalScanReport report = adapter.adapt(root, source);
            return new ReportImportResult(report, report.warnings(), report.errors());
        } catch (Exception exception) {
            return invalid("Invalid report: " + safeMessage(exception));
        }
    }

    private ReportImportResult invalid(String message) {
        CanonicalScanReport report = new CanonicalScanReport(1, ReportStatus.INVALID,
                new ReportMetadata(null, null, null, null, null, null, null, null),
                List.of(), List.of(), List.of(), List.of(message));
        return new ReportImportResult(report, List.of(), List.of(message));
    }

    private ReportImportResult unsupported(String message) {
        CanonicalScanReport report = new CanonicalScanReport(1, ReportStatus.UNSUPPORTED_FORMAT,
                new ReportMetadata(null, null, null, null, null, null, null, null),
                List.of(), List.of(), List.of(message), List.of());
        return new ReportImportResult(report, List.of(message), List.of());
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
