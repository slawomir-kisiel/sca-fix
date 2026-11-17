package pl.sca.fix.report.integration.owasp;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import pl.sca.fix.report.canonical.AffectedArtifact;
import pl.sca.fix.report.canonical.CanonicalScanReport;
import pl.sca.fix.report.canonical.VulnerabilityFinding;
import pl.sca.fix.report.integration.ScanReportAdapter;

@Component
public class OwaspDependencyCheckJsonAdapter implements ScanReportAdapter {
    private static final Pattern MAVEN_PURL = Pattern.compile("^pkg:maven/([^/]+)/([^@]+)@(.+)$");
    private static final Pattern CVE = Pattern.compile("^CVE-\\d{4}-\\d{4,}$", Pattern.CASE_INSENSITIVE);

    @Override public boolean supports(JsonNode document) {
        return document.has("reportSchema") && document.has("dependencies") && document.has("projectInfo");
    }

    @Override public CanonicalScanReport parse(JsonNode document) {
        JsonNode project = document.path("projectInfo");
        JsonNode scan = document.path("scanInfo");
        List<VulnerabilityFinding> findings = new ArrayList<>();
        for (JsonNode dependency : document.path("dependencies")) {
            AffectedArtifact artifact = artifact(dependency);
            for (JsonNode vulnerability : dependency.path("vulnerabilities")) {
                String id = text(vulnerability, "name");
                String cve = id != null && CVE.matcher(id.trim()).matches() ? id.trim() : null;
                JsonNode cvss = vulnerability.has("cvssv4") ? vulnerability.path("cvssv4")
                        : vulnerability.has("cvssv3") ? vulnerability.path("cvssv3") : vulnerability.path("cvssv2");
                findings.add(new VulnerabilityFinding(id, cve == null ? "OWASP_ID" : "CVE", "OWASP_DEPENDENCY_CHECK", cve,
                        text(vulnerability, "name"), text(vulnerability, "severity"),
                        text(vulnerability, "description"), null, text(cvss, "version"), number(cvss, "baseScore"),
                        text(cvss, "vectorString"), strings(vulnerability.path("cwes")), references(vulnerability.path("references")),
                        List.of(artifact)));
            }
        }
        return new CanonicalScanReport("OWASP_DEPENDENCY_CHECK", "JSON", text(document, "reportSchema"),
                new CanonicalScanReport.ScanMetadata(text(project, "name"), text(project, "groupID"),
                        text(project, "artifactID"), text(project, "version"), instant(project, "reportDate"), text(scan, "engineVersion")), findings);
    }

    private AffectedArtifact artifact(JsonNode dependency) {
        String purl = null;
        for (JsonNode item : dependency.path("packages")) if (item.has("id") && item.path("id").asText().startsWith("pkg:maven/")) { purl = item.path("id").asText(); break; }
        Matcher matcher = purl == null ? null : MAVEN_PURL.matcher(purl);
        return new AffectedArtifact(purl, matcher != null && matcher.matches() ? matcher.group(1) : null,
                matcher != null && matcher.matches() ? matcher.group(2) : null, matcher != null && matcher.matches() ? matcher.group(3) : null,
                text(dependency, "fileName"), text(dependency, "filePath"), text(dependency, "sha1"), text(dependency, "sha256"));
    }

    private List<String> strings(JsonNode node) { List<String> result = new ArrayList<>(); if (node.isArray()) node.forEach(item -> result.add(item.asText())); return result; }
    private List<String> references(JsonNode node) { List<String> result = new ArrayList<>(); if (node.isArray()) node.forEach(item -> { if (item.has("url")) result.add(item.path("url").asText()); }); return result; }
    private String text(JsonNode node, String field) { return node == null || node.path(field).isMissingNode() || node.path(field).isNull() ? null : node.path(field).asText(); }
    private Double number(JsonNode node, String field) { return node != null && node.has(field) && node.path(field).isNumber() ? node.path(field).doubleValue() : null; }
    private Instant instant(JsonNode node, String field) { try { return text(node, field) == null ? null : Instant.parse(text(node, field)); } catch (RuntimeException ignored) { return null; } }
}
