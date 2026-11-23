package pl.sca.fix.core.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class ScanReportFormatDetector {
    private final List<ScanReportAdapter> adapters;

    public ScanReportFormatDetector(List<ScanReportAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public ScanReportAdapter detect(JsonNode root, String formatHint) {
        return adapters.stream().filter(adapter -> adapter.supports(root, formatHint)).findFirst().orElse(null);
    }
}
