package pl.sca.fix.report.integration.checkmarx;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import pl.sca.fix.report.canonical.AffectedArtifact;
import pl.sca.fix.report.canonical.CanonicalScanReport;
import pl.sca.fix.report.canonical.VulnerabilityFinding;
import pl.sca.fix.report.integration.ScanReportAdapter;

@Component
public class CheckmarxJsonAdapter implements ScanReportAdapter {
    @Override public boolean supports(JsonNode document) {
        return document.has("results") && (document.has("project") || document.has("scan") || document.has("reportId"));
    }

    @Override public CanonicalScanReport parse(JsonNode document) {
        List<VulnerabilityFinding> findings = new ArrayList<>();
        for (JsonNode item : document.path("results")) {
            JsonNode issue = item.has("issue") ? item.path("issue") : item;
            String id = first(issue, "cve", "id", "name", "issueId");
            String severity = first(item, "severity", "severityName");
            String filePath = first(item.path("location"), "file", "filePath", "path");
            findings.add(new VulnerabilityFinding(id, id != null && id.startsWith("CVE-") ? "CVE" : "CHECKMARX_ID", "CHECKMARX",
                    id != null && id.startsWith("CVE-") ? id : null, first(issue, "title", "name", "description"), severity,
                    first(issue, "description", "details"), first(issue, "recommendation", "recommendations"),
                    first(item.path("cvss"), "version"), number(item.path("cvss"), "score"), first(item.path("cvss"), "vector", "vectorString"),
                    List.of(), List.of(), List.of(new AffectedArtifact(null, null, null, null, null, filePath, null, null))));
        }
        return new CanonicalScanReport("CHECKMARX", "JSON", first(document, "version", "schemaVersion"), null, findings);
    }

    private String first(JsonNode node, String... names) { for (String name : names) if (node != null && node.hasNonNull(name)) return node.path(name).asText(); return null; }
    private Double number(JsonNode node, String field) { return node != null && node.has(field) && node.path(field).isNumber() ? node.path(field).doubleValue() : null; }
}
