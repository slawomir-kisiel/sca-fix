package pl.sca.fix.report.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.sca.fix.report.integration.checkmarx.CheckmarxJsonAdapter;
import pl.sca.fix.report.integration.owasp.OwaspDependencyCheckJsonAdapter;

class ReportAdaptersTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsOwaspDependencyCheckJsonToCanonicalFindings() throws Exception {
        var document = mapper.readTree("""
                {"reportSchema":"1.1","scanInfo":{"engineVersion":"12.1.9"},"projectInfo":{"name":"demo","groupID":"com.example","artifactID":"demo","version":"1.0","reportDate":"2026-11-12T13:12:30.375676Z"},"dependencies":[{"fileName":"lib.jar","packages":[{"id":"pkg:maven/org.example/lib@1.2.3"}],"vulnerabilities":[{"source":"NVD","name":"CVE-2026-0001","severity":"HIGH","description":"description","cvssv3":{"version":"3.1","baseScore":8.8,"vectorString":"CVSS:3.1/AV:N"},"cwes":["CWE-79"],"references":[{"url":"https://example.test/CVE-2026-0001"}]}]}]}
                """);

        var result = new OwaspDependencyCheckJsonAdapter().parse(document);

        assertThat(result.source()).isEqualTo("OWASP_DEPENDENCY_CHECK");
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.cve()).isEqualTo("CVE-2026-0001");
            assertThat(finding.cvssScore()).isEqualTo(8.8);
            assertThat(finding.affectedArtifacts().getFirst().groupId()).isEqualTo("org.example");
        });
    }

    @Test
    void mapsCheckmarxJsonToCanonicalFindings() throws Exception {
        var document = mapper.readTree("""
                {"reportId":"scan-1","version":"2","project":{"name":"demo"},"results":[{"issue":{"id":"CX-1","title":"Issue","description":"Details"},"severity":"High","location":{"filePath":"src/Main.java"}}]}
                """);

        var result = new CheckmarxJsonAdapter().parse(document);

        assertThat(result.source()).isEqualTo("CHECKMARX");
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.externalId()).isEqualTo("CX-1");
            assertThat(finding.affectedArtifacts().getFirst().filePath()).isEqualTo("src/Main.java");
        });
    }

    @Test
    void mapsCheckmarxImprovedProjectReportToCanonicalFindings() throws Exception {
        var document = mapper.readTree("""
                {
                  "reportCreationDate":"2026-11-18T06:40:38.55280672+01:00",
                  "reportType":"Improved Project Report",
                  "projectsOverview":{"projectsList":[{"projectName":"P00972-iwa-kafka","projectBranchesOverview":[{"lastScan":"2026-11-15T22:01:12.139Z"}]}]},
                  "byScanner":{"scannersDistribution":[{"scannerName":"SCA","numberOfResults":1}]},
                  "topVulnerabilitiesList":{"vulnerabilitiesList":[{
                    "vulnerabilityType":"org.apache.kafka:kafka-clients:4.2.1 | CVE-2026-11095",
                    "total":2,
                    "vulnerabilitySeverities":[{"level":"Critical","value":5}]
                  }]}
                }
                """);

        var adapter = new CheckmarxJsonAdapter();
        var result = adapter.parse(document);

        assertThat(adapter.supports(document)).isTrue();
        assertThat(result.source()).isEqualTo("CHECKMARX");
        assertThat(result.formatVersion()).isEqualTo("Improved Project Report");
        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata().projectName()).isEqualTo("P00972-iwa-kafka");
        assertThat(result.metadata().engineVersion()).isEqualTo("SCA");
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.cve()).isEqualTo("CVE-2026-11095");
            assertThat(finding.identifierType()).isEqualTo("CVE");
            assertThat(finding.severity()).isEqualTo("Critical");
            assertThat(finding.affectedArtifacts()).singleElement().satisfies(artifact -> {
                assertThat(artifact.groupId()).isEqualTo("org.apache.kafka");
                assertThat(artifact.artifactId()).isEqualTo("kafka-clients");
                assertThat(artifact.version()).isEqualTo("4.2.1");
            });
        });
    }

    @Test
    void doesNotTreatOwaspAdvisoryTextAsCve() throws Exception {
        var document = mapper.readTree("""
                {"reportSchema":"1.1","scanInfo":{},"projectInfo":{},"dependencies":[{"vulnerabilities":[{"name":"Versions of Quill prior to 1.3.7 are vulnerable to Reverse Tabnabbing. The package should be upgraded."}]}]}
                """);

        var finding = new OwaspDependencyCheckJsonAdapter().parse(document).findings().getFirst();

        assertThat(finding.cve()).isNull();
        assertThat(finding.identifierType()).isEqualTo("OWASP_ID");
        assertThat(finding.externalId()).startsWith("Versions of Quill");
    }
}
