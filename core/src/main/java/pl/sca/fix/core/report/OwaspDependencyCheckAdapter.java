package pl.sca.fix.core.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.sca.fix.core.port.ReportDocument;

public final class OwaspDependencyCheckAdapter implements ScanReportAdapter {
    @Override public String format() { return "OWASP"; }

    @Override public boolean supports(JsonNode root, String hint) {
        if (hint != null && hint.equalsIgnoreCase("checkmarx")) return false;
        return hint != null && (hint.equalsIgnoreCase("owasp") || hint.equalsIgnoreCase("dependency-check"))
                || root.has("dependencies") && root.has("projectInfo");
    }

    @Override public CanonicalScanReport adapt(JsonNode root, ReportDocument source) {
        JsonNode project = root.path("projectInfo");
        JsonNode scan = root.path("scanInfo");
        Map<String, LibraryBuilder> libraries = new LinkedHashMap<>();
        Map<String, CveBuilder> cves = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (JsonNode dependency : root.path("dependencies")) {
            JsonNode vulnerabilities = dependency.path("vulnerabilities");
            if (!vulnerabilities.isArray() || vulnerabilities.isEmpty()) continue;
            String[] coordinate = coordinate(dependency);
            if (coordinate == null) {
                String packageId = firstPackageId(dependency);
                if (packageId != null && !packageId.startsWith("pkg:maven/")) {
                    warnings.add("Non-Maven package ignored: " + packageId);
                } else {
                    warnings.add("Maven coordinate not found for dependency: "
                            + ReportMappingSupport.text(dependency, "fileName"));
                }
                continue;
            }
            LibraryBuilder library = libraries.computeIfAbsent(coordinate[0] + ':' + coordinate[1] + ':' + coordinate[2],
                    ignored -> new LibraryBuilder(coordinate[0], coordinate[1], coordinate[2]));
            for (JsonNode vulnerability : vulnerabilities) {
                String signature = ReportMappingSupport.text(vulnerability, "name");
                if (signature == null || signature.isBlank()) { warnings.add("OWASP vulnerability without signature"); continue; }
                BigDecimal score = ReportMappingSupport.score(vulnerability);
                Severity severity = ReportMappingSupport.severity(vulnerability);
                library.add(signature, severity, score, ReportMappingSupport.recommendedVersions(vulnerability));
                cves.computeIfAbsent(signature, ignored -> new CveBuilder(signature)).merge(vulnerability, severity, score);
            }
        }
        ReportMetadata metadata = new ReportMetadata("OWASP", ReportMappingSupport.text(root, "reportSchema"),
                source.sourceFile().getFileName().toString(), ReportMappingSupport.instant(ReportMappingSupport.text(project, "reportDate")),
                ReportMappingSupport.instant(ReportMappingSupport.text(project, "reportDate")),
                ReportMappingSupport.text(scan, "engineVersion"), null,
                new ReportMetadata.ProjectContext(ReportMappingSupport.text(project, "name"), ReportMappingSupport.text(project, "groupID"),
                        ReportMappingSupport.text(project, "artifactID"), ReportMappingSupport.text(project, "version")));
        return new CanonicalScanReport(1, ReportStatus.PARSED, metadata,
                libraries.values().stream().map(LibraryBuilder::build).toList(),
                cves.values().stream().map(CveBuilder::build).toList(), warnings, List.of());
    }

    private static String[] coordinate(JsonNode dependency) {
        for (JsonNode packageNode : dependency.path("packages")) {
            String[] coordinate = ReportMappingSupport.coordinateFromPurl(ReportMappingSupport.text(packageNode, "id"));
            if (coordinate != null) return coordinate;
        }
        String group = ReportMappingSupport.text(dependency, "groupId");
        String artifact = ReportMappingSupport.text(dependency, "artifactId");
        String version = ReportMappingSupport.text(dependency, "version");
        return group == null || artifact == null || version == null ? null : new String[] {group, artifact, version};
    }

    private static String firstPackageId(JsonNode dependency) {
        for (JsonNode packageNode : dependency.path("packages")) {
            String id = ReportMappingSupport.text(packageNode, "id");
            if (id != null) return id;
        }
        return null;
    }

    private static final class LibraryBuilder {
        private final String groupId, artifactId, version;
        private final Set<String> cve = new LinkedHashSet<>(), recommended = new LinkedHashSet<>();
        private Severity severity = Severity.UNKNOWN;
        private BigDecimal cvss;
        private LibraryBuilder(String groupId, String artifactId, String version) { this.groupId = groupId; this.artifactId = artifactId; this.version = version; }
        private void add(String signature, Severity value, BigDecimal score, List<String> versions) {
            cve.add(signature); recommended.addAll(versions); severity = Severity.max(severity, value);
            if (score != null && (cvss == null || score.compareTo(cvss) > 0)) cvss = score;
        }
        private VulnerableLibrary build() { return new VulnerableLibrary(groupId, artifactId, version, List.copyOf(cve), severity, cvss, List.copyOf(recommended)); }
    }

    private static final class CveBuilder {
        private final String signature; private BigDecimal cvss; private Severity severity = Severity.UNKNOWN;
        private String title, description, recommendation; private final Set<String> references = new LinkedHashSet<>();
        private CveBuilder(String signature) { this.signature = signature; }
        private void merge(JsonNode node, Severity value, BigDecimal score) {
            severity = Severity.max(severity, value); if (score != null && (cvss == null || score.compareTo(cvss) > 0)) cvss = score;
            description = description == null ? ReportMappingSupport.text(node, "description") : description;
            recommendation = recommendation == null ? ReportMappingSupport.text(node, "recommendation") : recommendation;
            for (JsonNode reference : node.path("references")) { String url = ReportMappingSupport.text(reference, "url"); if (url != null) references.add(url); }
        }
        private CveDetails build() { return new CveDetails(signature, cvss, severity, title, description, recommendation, List.copyOf(references)); }
    }
}
