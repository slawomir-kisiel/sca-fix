package pl.sca.fix.core.report;

import com.fasterxml.jackson.databind.JsonNode;
import pl.sca.fix.core.port.ReportDocument;

public interface ScanReportAdapter {
    String format();
    boolean supports(JsonNode root, String formatHint);
    CanonicalScanReport adapt(JsonNode root, ReportDocument source);
}
