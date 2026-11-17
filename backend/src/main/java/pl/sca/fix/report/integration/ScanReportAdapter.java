package pl.sca.fix.report.integration;

import com.fasterxml.jackson.databind.JsonNode;
import pl.sca.fix.report.canonical.CanonicalScanReport;

public interface ScanReportAdapter {
    boolean supports(JsonNode document);
    CanonicalScanReport parse(JsonNode document);
}
