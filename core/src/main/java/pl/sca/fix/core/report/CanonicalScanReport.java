package pl.sca.fix.core.report;

import java.util.List;
import java.util.LinkedHashSet;

public record CanonicalScanReport(int schemaVersion, ReportStatus status, ReportMetadata metadata,
                                  List<VulnerableLibrary> vulnerableLibraries,
                                  List<CveDetails> cves, List<String> warnings, List<String> errors) {
    public CanonicalScanReport {
        vulnerableLibraries = List.copyOf(vulnerableLibraries == null ? List.of() : vulnerableLibraries);
        cves = List.copyOf(cves == null ? List.of() : cves);
        warnings = List.copyOf(new LinkedHashSet<>(warnings == null ? List.of() : warnings));
        errors = List.copyOf(new LinkedHashSet<>(errors == null ? List.of() : errors));
    }
}
