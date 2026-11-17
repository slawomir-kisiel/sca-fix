package pl.sca.fix.report.integration;

import pl.sca.fix.report.canonical.CanonicalScanReport;

public record ReportDetectionResult(String source, String format, String formatVersion,
                                    CanonicalScanReport canonicalReport) { }
