package pl.sca.fix.core.report;

import java.util.List;

public record ReportImportResult(CanonicalScanReport report, List<String> warnings, List<String> errors) {
    public ReportImportResult {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        errors = List.copyOf(errors == null ? List.of() : errors);
    }
}
