package pl.sca.fix.report.integration.checkmarx;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import pl.sca.fix.report.canonical.AffectedArtifact;
import pl.sca.fix.report.canonical.CanonicalScanReport;
import pl.sca.fix.report.canonical.VulnerabilityFinding;
import pl.sca.fix.report.integration.ScanReportAdapter;

@Component
public class CheckmarxJsonAdapter implements ScanReportAdapter {
    private static final Pattern VULNERABILITY_TYPE = Pattern.compile("(?<package>[^|]+)\\s*\\|\\s*(?<id>.+)$");
    private static final Pattern MAVEN_COORDINATES = Pattern.compile("(?<group>[^:]+):(?<artifactAndVersion>[^:]+):(?<version>[^:]+)");
    private static final Pattern CVE = Pattern.compile("CVE-\\d{4}-\\d{4,}", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(JsonNode document) {
        boolean legacyResults = document.has("results")
                && (document.has("project") || document.has("scan") || document.has("reportId"));
        boolean improvedProjectReport = document.has("reportType")
                && document.path("topVulnerabilitiesList").has("vulnerabilitiesList");
        return legacyResults || improvedProjectReport;
    }

    @Override
    public CanonicalScanReport parse(JsonNode document) {
        if (document.has("results")) return parseLegacy(document);
        return parseImprovedProjectReport(document);
    }

    private CanonicalScanReport parseLegacy(JsonNode document) {
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

    private CanonicalScanReport parseImprovedProjectReport(JsonNode document) {
        List<VulnerabilityFinding> findings = new ArrayList<>();
        for (JsonNode item : document.path("topVulnerabilitiesList").path("vulnerabilitiesList")) {
            String vulnerabilityType = text(item, "vulnerabilityType");
            if (blank(vulnerabilityType)) continue;

            ParsedVulnerability parsed = parseVulnerabilityType(vulnerabilityType);
            String identifier = parsed.id();
            String cve = isCve(identifier) ? identifier : null;
            String externalId = cve == null ? vulnerabilityType : cve;
            String description = "Aggregated occurrences: " + integer(item, "total");
            findings.add(new VulnerabilityFinding(externalId, cve == null ? "CHECKMARX_ID" : "CVE", "CHECKMARX", cve,
                    vulnerabilityType, dominantSeverity(item.path("vulnerabilitySeverities")), description,
                    null, null, null, null, List.of(), List.of(),
                    parsed.artifact() == null ? List.of() : List.of(parsed.artifact())));
        }

        JsonNode project = firstArrayItem(document.path("projectsOverview").path("projectsList"));
        JsonNode branch = firstArrayItem(project.path("projectBranchesOverview"));
        CanonicalScanReport.ScanMetadata metadata = new CanonicalScanReport.ScanMetadata(
                text(project, "projectName"), null, null, null,
                firstInstant(branch, "lastScan"), primaryScanner(document.path("byScanner").path("scannersDistribution")));
        return new CanonicalScanReport("CHECKMARX", "JSON", first(document, "version", "schemaVersion", "reportType"), metadata, findings);
    }

    private ParsedVulnerability parseVulnerabilityType(String vulnerabilityType) {
        Matcher matcher = VULNERABILITY_TYPE.matcher(vulnerabilityType);
        String packagePart = matcher.matches() ? matcher.group("package").trim() : null;
        String id = matcher.matches() ? matcher.group("id").trim() : vulnerabilityType;
        return new ParsedVulnerability(id, parseMavenArtifact(packagePart));
    }

    private AffectedArtifact parseMavenArtifact(String packagePart) {
        if (blank(packagePart)) return null;
        Matcher matcher = MAVEN_COORDINATES.matcher(packagePart.trim());
        if (!matcher.matches()) return null;
        String groupId = matcher.group("group").trim();
        String artifactId = matcher.group("artifactAndVersion").trim();
        String version = matcher.group("version").trim();
        String purl = "pkg:maven/" + groupId + "/" + artifactId + "@" + version;
        return new AffectedArtifact(purl, groupId, artifactId, version, null, null, null, null);
    }

    private String dominantSeverity(JsonNode severities) {
        if (severities == null || !severities.isArray() || severities.isEmpty()) return null;
        return stream(severities)
                .max(Comparator.comparingInt((JsonNode item) -> item.path("value").asInt())
                        .thenComparingInt(item -> severityRank(text(item, "level"))))
                .map(item -> text(item, "level"))
                .orElse(null);
    }

    private int severityRank(String severity) {
        if (severity == null) return 0;
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            case "LOW" -> 2;
            case "INFO", "INFORMATIONAL" -> 1;
            default -> 0;
        };
    }

    private String primaryScanner(JsonNode scanners) {
        if (scanners == null || !scanners.isArray() || scanners.isEmpty()) return null;
        return stream(scanners)
                .filter(item -> item.path("numberOfResults").asInt() > 0)
                .max(Comparator.comparingInt(item -> item.path("numberOfResults").asInt()))
                .map(item -> text(item, "scannerName"))
                .orElse(null);
    }

    private JsonNode firstArrayItem(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node.path(0) : null;
    }

    private Instant firstInstant(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (blank(value)) continue;
            try { return Instant.parse(value); }
            catch (RuntimeException ignored) {
                try { return OffsetDateTime.parse(value).toInstant(); }
                catch (RuntimeException ignoredAgain) { }
            }
        }
        return null;
    }

    private boolean isCve(String id) { return id != null && CVE.matcher(id.trim()).matches(); }

    private String first(JsonNode node, String... names) {
        for (String name : names) {
            String value = text(node, name);
            if (!blank(value)) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() || node.path(field).isNull() ? null : node.path(field).asText();
    }

    private int integer(JsonNode node, String field) {
        return node != null && node.path(field).isNumber() ? node.path(field).asInt() : 0;
    }

    private Double number(JsonNode node, String field, boolean decimal) {
        return node != null && node.path(field).isNumber() ? node.path(field).doubleValue() : null;
    }

    private Double number(JsonNode node, String field) {
        return number(node, field, true);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items.stream();
    }

    private record ParsedVulnerability(String id, AffectedArtifact artifact) { }
}
