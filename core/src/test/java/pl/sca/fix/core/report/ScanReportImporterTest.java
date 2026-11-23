package pl.sca.fix.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.port.ReportDocument;

class ScanReportImporterTest {
    @Test
    void mapsOwaspDependencyCheckReportAndAggregatesSeverity() throws Exception {
        ReportDocument source = document("""
                {"reportSchema":"1.1","scanInfo":{"engineVersion":"12.1.9"},
                 "projectInfo":{"name":"demo","groupID":"com.demo","artifactID":"app","version":"1"},
                 "dependencies":[{"packages":[{"id":"pkg:maven/org.example/library@1.0"}],"vulnerabilities":[
                   {"name":"CVE-1","severity":"MEDIUM","cvssv3":{"baseScore":5.44},"description":"one","recommendation":"upgrade to version 1.2"},
                   {"name":"CVE-2","severity":"CRITICAL","cvssv3":{"baseScore":9.11},"description":"two"}
                 ]}]}
                """);

        CanonicalScanReport report = new ScanReportImporter().importReport(source).report();

        assertEquals(ReportStatus.PARSED, report.status());
        assertEquals("demo", report.metadata().project().name());
        assertEquals(1, report.vulnerableLibraries().size());
        VulnerableLibrary library = report.vulnerableLibraries().get(0);
        assertEquals("org.example", library.groupId());
        assertEquals(Severity.CRITICAL, library.severity());
        assertEquals("9.1", library.cvss().toPlainString());
        assertEquals(java.util.List.of("1.2"), library.recommendedVersion());
        assertEquals(2, report.cves().size());
    }

    @Test
    void mapsCheckmarxPackageAndUsesResultIdWhenCveIsMissing() {
        ReportDocument source = document("""
                {"reportCreationDate":"2026-11-22T10:00:00Z",
                 "reportHeader":{"projectName":"cx-project","scannedBranchName":"master"},
                 "scaScanResults":{"packages":[{"packageName":"org.example:library","packageVersion":"2.0",
                   "packageCategory":[{"categoryResults":[{"resultId":"result-1","severity":"High",
                     "meta":{"description":"description","recommendedVersion":"2.1"}}]}]}]}}
                """);

        CanonicalScanReport report = new ScanReportImporter().importReport(source).report();

        assertEquals(ReportStatus.PARSED, report.status());
        assertEquals("CHECKMARX", report.metadata().source());
        assertEquals("cx-project", report.metadata().project().name());
        assertEquals("result-1", report.vulnerableLibraries().get(0).cve().get(0));
        assertEquals("2.1", report.vulnerableLibraries().get(0).recommendedVersion().get(0));
    }

    @Test
    void importsTheProvidedOwaspFixture() throws Exception {
        Path fixture = Path.of("docs/examples/dependency-check-report.json");
        if (!Files.isRegularFile(fixture)) return;
        CanonicalScanReport report = new ScanReportImporter().importReport(new ReportDocument(
                Files.readAllBytes(fixture), fixture, "owasp")).report();
        assertEquals(ReportStatus.PARSED, report.status());
        assertTrue(report.vulnerableLibraries().size() > 0);
        assertTrue(report.cves().size() > 0);
        assertEquals(report.warnings().stream().distinct().count(), report.warnings().size());
    }

    @Test
    void canonicalJsonRoundTripPreservesModel() {
        CanonicalScanReport original = new CanonicalScanReport(1, ReportStatus.PARSED,
                new ReportMetadata("OWASP", "1.1", "report.json", null, null, "12.1.9", null, null),
                java.util.List.of(new VulnerableLibrary("g", "a", "1", java.util.List.of("CVE-1"),
                        Severity.HIGH, new java.math.BigDecimal("7.5"), java.util.List.of("2"))),
                java.util.List.of(new CveDetails("CVE-1", new java.math.BigDecimal("7.5"), Severity.HIGH,
                        null, "description", null, java.util.List.of())), java.util.List.of(), java.util.List.of());

        CanonicalReportJsonCodec codec = new CanonicalReportJsonCodec();
        CanonicalScanReport restored = codec.read(codec.write(original));

        assertEquals(original, restored);
        assertNotNull(restored.vulnerableLibraries());
    }

    @Test
    void reportsUnsupportedFormatWithoutThrowing() {
        ReportImportResult result = new ScanReportImporter().importReport(document("{\"hello\":true}"));
        assertEquals(ReportStatus.UNSUPPORTED_FORMAT, result.report().status());
        assertTrue(result.warnings().get(0).contains("Unsupported"));
    }

    private static ReportDocument document(String json) {
        return new ReportDocument(json.getBytes(StandardCharsets.UTF_8), Path.of("report.json"), null);
    }
}
