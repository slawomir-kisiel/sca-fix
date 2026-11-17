package pl.sca.fix.report.canonical;

import java.time.Instant;
import java.util.List;

public record CanonicalScanReport(
        String source,
        String format,
        String formatVersion,
        ScanMetadata metadata,
        List<VulnerabilityFinding> findings) {
    public CanonicalScanReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public record ScanMetadata(String projectName, String groupId, String artifactId,
                               String projectVersion, Instant scanDate, String engineVersion) { }
}
