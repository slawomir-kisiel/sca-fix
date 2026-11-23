package pl.sca.fix.core.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import pl.sca.fix.core.port.ReportDocument;

public final class CheckmarxJsonAdapter implements ScanReportAdapter {
    private static final Pattern IMPROVED_PACKAGE = Pattern.compile("^Maven-([^:]+):(.+)-(\\d[\\w.+-]*)$");

    @Override public String format() { return "CHECKMARX"; }

    @Override public boolean supports(JsonNode root, String hint) {
        if (hint != null && (hint.equalsIgnoreCase("checkmarx") || hint.equalsIgnoreCase("cx"))) return true;
        return root.has("scaScanResults") || root.has("reportHeader") || root.has("projectOverview")
                || root.has("resultsOverview") || root.has("topTenVulnerabilityType");
    }

    @Override public CanonicalScanReport adapt(JsonNode root, ReportDocument source) {
        if (root.has("resultsOverview") || root.has("topTenVulnerabilityType")) {
            return adaptImprovedProjectReport(root, source);
        } else {
            return adaptScanReport(root, source);
        }
    }

    private CanonicalScanReport adaptScanReport(JsonNode root, ReportDocument source) {
        JsonNode header = root.path("reportHeader");
        JsonNode scan = root.path("scanInformation");
        JsonNode results = root.path("scaScanResults");
        Map<String, LibraryBuilder> libraries = new LinkedHashMap<>();
        Map<String, CveBuilder> cves = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (JsonNode packageNode : results.path("packages")) {
            String[] coordinate = coordinate(packageNode);
            if (coordinate == null) {
                warnings.add("Checkmarx package is not a Maven coordinate: "
                        + packageNode.path("packageName").asText());
                continue;
            }
            LibraryBuilder library = libraries.computeIfAbsent(
                    coordinate[0] + ':' + coordinate[1] + ':' + coordinate[2],
                    ignored -> new LibraryBuilder(coordinate[0], coordinate[1], coordinate[2]));
            collectCategories(packageNode.path("packageCategory"), library, cves);
        }
        String projectName = first(ReportMappingSupport.text(header, "projectName"),
                ReportMappingSupport.text(scan, "projectName"));
        String branch = first(ReportMappingSupport.text(header, "scannedBranchName"),
                ReportMappingSupport.text(scan, "branch"));
        ReportMetadata metadata = new ReportMetadata("CHECKMARX", null,
                source.sourceFile().getFileName().toString(),
                ReportMappingSupport.instant(ReportMappingSupport.text(root, "reportCreationDate")),
                ReportMappingSupport.instant(ReportMappingSupport.text(header, "scanDate")), null,
                new ReportMetadata.RepositoryContext(null, null, branch),
                new ReportMetadata.ProjectContext(projectName, null, null, null));
        return report(metadata, libraries, cves, warnings);
    }

    private CanonicalScanReport adaptImprovedProjectReport(JsonNode root, ReportDocument source) {
        Map<String, LibraryBuilder> libraries = new LinkedHashMap<>();
        Map<String, CveBuilder> cves = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (JsonNode result : root.path("resultsOverview")) {
            String[] coordinate = improvedCoordinate(ReportMappingSupport.text(result, "name"));
            if (coordinate == null) {
                continue;
            }
            LibraryBuilder library = library(libraries, coordinate);
            library.severity = Severity.max(library.severity,
                    dominantSeverity(result.path("severityBreakdown")));
        }
        JsonNode vulnerabilityTypes = root.path("topTenVulnerabilityType").path("vulnerabilitiesList");
        for (JsonNode item : vulnerabilityTypes) {
            String vulnerabilityType = ReportMappingSupport.text(item, "vulnerabilityType");
            if (vulnerabilityType == null) {
                continue;
            }
            int delimiter = vulnerabilityType.lastIndexOf('|');
            if (delimiter < 0) {
                continue;
            }
            String[] coordinate = improvedCoordinate(vulnerabilityType.substring(0, delimiter).trim());
            String signature = vulnerabilityType.substring(delimiter + 1).trim();
            if (coordinate == null || signature.isEmpty()) {
                continue;
            }
            Severity severity = dominantSeverity(item.path("vulnerabilitySeverities"));
            library(libraries, coordinate).add(signature, severity, null, List.of());
            CveBuilder cve = cves.computeIfAbsent(signature, CveBuilder::new);
            cve.title = vulnerabilityType;
        }
        JsonNode project = root.path("projectsOverview").path("projectsList").path(0);
        JsonNode branch = project.path("projectBranchesOverview").path(0);
        JsonNode lastScan = branch.path("lastScan");
        String branchName = ReportMappingSupport.text(branch, "branchName");
        if (!vulnerabilityTypes.isMissingNode()) {
            warnings.add("Checkmarx Improved Project Report contains only top-ten vulnerability details; "
                    + "CVE data may be incomplete.");
        }
        ReportMetadata metadata = new ReportMetadata("CHECKMARX",
                ReportMappingSupport.text(root, "reportType"), source.sourceFile().getFileName().toString(),
                ReportMappingSupport.instant(ReportMappingSupport.text(root, "reportCreationDate")),
                ReportMappingSupport.instant(ReportMappingSupport.text(lastScan, "lastScanDate")), null,
                new ReportMetadata.RepositoryContext(null, null, branchName),
                new ReportMetadata.ProjectContext(ReportMappingSupport.text(project, "projectName"), null, null, null));
        return report(metadata, libraries, cves, warnings);
    }

    private static CanonicalScanReport report(ReportMetadata metadata, Map<String, LibraryBuilder> libraries,
            Map<String, CveBuilder> cves, List<String> warnings) {
        return new CanonicalScanReport(1, ReportStatus.PARSED, metadata,
                libraries.values().stream().map(LibraryBuilder::build).toList(),
                cves.values().stream().map(CveBuilder::build).toList(), warnings, List.of());
    }

    private static LibraryBuilder library(Map<String, LibraryBuilder> libraries, String[] coordinate) {
        String key = coordinate[0] + ':' + coordinate[1] + ':' + coordinate[2];
        return libraries.computeIfAbsent(key, ignored -> new LibraryBuilder(coordinate[0], coordinate[1], coordinate[2]));
    }

    private static void collectCategories(JsonNode categories, LibraryBuilder library, Map<String, CveBuilder> cves) {
        if (!categories.isArray()) return;
        for (JsonNode category : categories)
            for (JsonNode result : category.path("categoryResults")) {
                JsonNode meta = result.path("meta");
                String signature = ReportMappingSupport.text(meta, "cve");
                if (signature == null || signature.isBlank()) {
                    signature = ReportMappingSupport.text(result, "cwe");
                }
                if (signature == null || signature.isBlank()) {
                    signature = ReportMappingSupport.text(result, "cve");
                }
                if (signature == null || signature.isBlank()) {
                    signature = ReportMappingSupport.text(result, "resultId");
                }
                if (signature == null || signature.isBlank()) {
                    continue;
                }
                Severity severity = Severity.parse(ReportMappingSupport.text(result, "severity"));
                BigDecimal score = ReportMappingSupport.score(result);
                if (score == null) {
                    score = ReportMappingSupport.score(meta);
                }
                Set<String> recommended = new LinkedHashSet<>(ReportMappingSupport.recommendedVersions(meta));
                recommended.addAll(ReportMappingSupport.recommendedVersions(result));
                library.add(signature, severity, score, List.copyOf(recommended));
                cves.computeIfAbsent(signature, CveBuilder::new).merge(result, meta, severity, score);
        }
    }

    private static String[] coordinate(JsonNode packageNode) {
        String packageName = ReportMappingSupport.text(packageNode, "packageName");
        String version = ReportMappingSupport.text(packageNode, "packageVersion");
        if (packageName == null || version == null) return null;
        int separator = packageName.indexOf(':');
        if (separator <= 0 || separator == packageName.length() - 1) return null;
        return new String[]{packageName.substring(0, separator), packageName.substring(separator + 1), version};
    }

    private static String[] improvedCoordinate(String packageName) {
        if (packageName == null) {
            return null;
        }
        Matcher matcher = IMPROVED_PACKAGE.matcher(packageName.trim());
        return matcher.matches() ? new String[]{matcher.group(1), matcher.group(2), matcher.group(3)} : null;
    }

    private static Severity dominantSeverity(JsonNode breakdown) {
        Severity dominant = Severity.UNKNOWN;
        if (!breakdown.isArray()) {
            return dominant;
        }
        for (JsonNode item : breakdown) {
            if (ReportMappingSupport.score(item) != null) {
                dominant = Severity.max(dominant, Severity.parse(ReportMappingSupport.text(item, "level")));
            }
        }
        return dominant;
    }

    private static String first(String left, String right) { return left == null || left.isBlank() ? right : left; }

    private static final class LibraryBuilder {
        private final String groupId, artifactId, version;
        private final Set<String> cve = new LinkedHashSet<>(), recommended = new LinkedHashSet<>();
        private Severity severity = Severity.UNKNOWN;
        private BigDecimal cvss;

        private LibraryBuilder(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
        private void add(String signature, Severity value, BigDecimal score, List<String> versions) {
            cve.add(signature); recommended.addAll(versions); severity = Severity.max(severity, value);
            if (score != null && (cvss == null || score.compareTo(cvss) > 0)) cvss = score;
        }
        private VulnerableLibrary build() {
            return new VulnerableLibrary(groupId, artifactId, version, List.copyOf(cve), severity, cvss, List.copyOf(recommended));
        }
    }

    private static final class CveBuilder {
        private final String signature;
        private BigDecimal cvss;
        private Severity severity = Severity.UNKNOWN;
        private String title, description, recommendation;
        private final Set<String> references = new LinkedHashSet<>();
        private CveBuilder(String signature) { this.signature = signature; }
        private void merge(JsonNode result, JsonNode meta, Severity value, BigDecimal score) {
            severity = Severity.max(severity, value);
            if (score != null && (cvss == null || score.compareTo(cvss) > 0)) {
                cvss = score;
            }
            title = first(title, ReportMappingSupport.text(result, "title"), ReportMappingSupport.text(meta, "title"));
            description = first(description, ReportMappingSupport.text(result, "description"), ReportMappingSupport.text(meta, "description"));
            recommendation = first(recommendation, ReportMappingSupport.text(result, "recommendedVersion"),
                    ReportMappingSupport.text(meta, "recommendedVersion"), ReportMappingSupport.text(result, "recommendation"),
                    ReportMappingSupport.text(meta, "recommendation"));
            addReferences(meta.path("references"));
            addReferences(result.path("references"));
        }

        private void addReferences(JsonNode values) {
            if (!values.isArray()) {
                return;
            }
            for (JsonNode reference : values) {
                String url = reference.isValueNode() ? reference.asText() : ReportMappingSupport.text(reference, "url");
                if (url != null && !url.isBlank()) {
                    references.add(url);
                }
            }
        }
        private CveDetails build() { return new CveDetails(signature, cvss, severity, title, description, recommendation, List.copyOf(references)); }
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
