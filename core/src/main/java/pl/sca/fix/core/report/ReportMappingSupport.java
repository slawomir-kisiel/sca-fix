package pl.sca.fix.core.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReportMappingSupport {
    private static final Pattern VERSION = Pattern.compile("(?i)(?:version|upgrade(?:d)? to|fixed in)\\s+([0-9][A-Za-z0-9_.-]*)");

    private ReportMappingSupport() { }

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) {
            try { return OffsetDateTime.parse(value).toInstant(); }
            catch (DateTimeParseException ignoredAgain) { return null; }
        }
    }

    static BigDecimal score(JsonNode vulnerability) {
        BigDecimal best = null;
        for (String key : List.of("cvssv4", "cvssv3", "cvssv2", "cvss")) {
            JsonNode value = vulnerability.get(key);
            if (value == null || value.isNull()) continue;
            JsonNode score = value.isValueNode() ? value : value.get("baseScore");
            if (score != null && score.isNumber()) {
                BigDecimal candidate = score.decimalValue().setScale(1, RoundingMode.HALF_UP);
                if (best == null || candidate.compareTo(best) > 0) best = candidate;
            }
        }
        return best;
    }

    static Severity severity(JsonNode vulnerability) {
        Severity result = Severity.parse(text(vulnerability, "severity"));
        for (String key : List.of("cvssv4", "cvssv3", "cvssv2")) {
            JsonNode cvss = vulnerability.get(key);
            if (cvss != null) result = Severity.max(result, Severity.parse(text(cvss, "baseSeverity")));
        }
        return result;
    }

    static List<String> recommendedVersions(JsonNode vulnerability) {
        Set<String> versions = new LinkedHashSet<>();
        String direct = text(vulnerability, "recommendedVersion");
        if (direct != null && !direct.isBlank()) versions.add(direct.trim());
        String recommendation = text(vulnerability, "recommendation");
        if (recommendation != null) {
            Matcher matcher = VERSION.matcher(recommendation);
            while (matcher.find()) versions.add(matcher.group(1));
        }
        return List.copyOf(versions);
    }

    static String[] coordinateFromPurl(String purl) {
        if (purl == null || !purl.startsWith("pkg:maven/")) return null;
        String value = purl.substring("pkg:maven/".length());
        int at = value.indexOf('@');
        if (at < 0) return null;
        String coordinate = value.substring(0, at);
        String version = value.substring(at + 1);
        int slash = coordinate.lastIndexOf('/');
        if (slash <= 0 || slash == coordinate.length() - 1 || version.isBlank()) return null;
        return new String[] {coordinate.substring(0, slash), coordinate.substring(slash + 1), version};
    }

    static List<String> strings(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        JsonNode values = node == null ? null : node.get(field);
        if (values != null && values.isArray()) values.forEach(value -> result.add(value.asText()));
        return result;
    }
}
