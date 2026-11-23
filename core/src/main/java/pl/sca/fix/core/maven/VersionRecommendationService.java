package pl.sca.fix.core.maven;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.report.UpdateLevel;

/** Selects the highest newer version allowed by the configured update level. */
public final class VersionRecommendationService {
    private static final Logger LOG = LoggerFactory.getLogger(VersionRecommendationService.class);

    public VersionRecommendation recommend(String groupId, String artifactId, String currentVersion,
                                           List<VersionCacheDocument.VersionEntry> candidates,
                                           UpdateLevel requestedLevel) {
        requireText(groupId, "groupId");
        requireText(artifactId, "artifactId");
        requireText(currentVersion, "currentVersion");
        UpdateLevel level = requestedLevel == null ? UpdateLevel.MINOR : requestedLevel;
        List<VersionCacheDocument.VersionEntry> unique = uniqueCandidates(candidates);
        LOG.info("Starting version recommendation artifact={}:{} currentVersion={} level={} candidateCount={}",
                groupId, artifactId, currentVersion, level, unique.size());

        Map<String, List<String>> rejected = new LinkedHashMap<>();
        List<String> eligible = new ArrayList<>();
        for (VersionCacheDocument.VersionEntry candidate : unique) {
            String version = candidate.version();
            String reason = rejectionReason(currentVersion, version, level);
            if (reason == null) eligible.add(version);
            else rejected.computeIfAbsent(reason, ignored -> new ArrayList<>()).add(version);
        }
        eligible.sort(new MavenVersionComparator().reversed());
        String recommendation = eligible.isEmpty() ? null : eligible.get(0);
        List<VersionRejection> rejections = rejected.entrySet().stream()
                .map(entry -> new VersionRejection(entry.getKey(), reasonText(entry.getKey()),
                        entry.getValue().stream().sorted(new MavenVersionComparator().reversed()).toList()))
                .toList();
        LOG.info("Finished version recommendation artifact={}:{} recommendedVersion={} rejectedCount={}",
                groupId, artifactId, recommendation, unique.size() - eligible.size());
        return new VersionRecommendation(groupId, artifactId, currentVersion, level,
                recommendation, eligible, rejections);
    }

    private static List<VersionCacheDocument.VersionEntry> uniqueCandidates(
            List<VersionCacheDocument.VersionEntry> candidates) {
        Map<String, VersionCacheDocument.VersionEntry> unique = new LinkedHashMap<>();
        for (VersionCacheDocument.VersionEntry candidate : candidates == null ? List.<VersionCacheDocument.VersionEntry>of() : candidates) {
            if (candidate != null && candidate.version() != null && !candidate.version().isBlank())
                unique.putIfAbsent(candidate.version(), candidate);
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(VersionCacheDocument.VersionEntry::version,
                        new MavenVersionComparator()))
                .toList();
    }

    private static String rejectionReason(String current, String candidate, UpdateLevel level) {
        if (MavenVersionComparator.compareVersions(candidate, current) <= 0)
            return "NOT_NEWER_THAN_CURRENT";
        if (!MavenVersionComparator.qualifier(current).equalsIgnoreCase(
                MavenVersionComparator.qualifier(candidate)))
            return "QUALIFIER_MISMATCH";
        int[] expected = numericParts(current);
        int[] actual = numericParts(candidate);
        if (expected.length == 0 || actual.length == 0) return "INVALID_VERSION";
        if (level == UpdateLevel.PATCH && actual[0] != expected[0]) return "MAJOR_CHANGE_NOT_ALLOWED";
        if (level == UpdateLevel.PATCH && value(actual, 1) != value(expected, 1))
            return "MINOR_CHANGE_NOT_ALLOWED";
        if (level == UpdateLevel.MINOR && actual[0] != expected[0])
            return "MAJOR_CHANGE_NOT_ALLOWED";
        return null;
    }

    private static String reasonText(String code) {
        return switch (code) {
            case "NOT_NEWER_THAN_CURRENT" -> "Version is not newer than current version";
            case "MINOR_CHANGE_NOT_ALLOWED" -> "Minor version differs from current version";
            case "MAJOR_CHANGE_NOT_ALLOWED" -> "Major version differs from current version";
            case "INVALID_VERSION" -> "Version has no numeric Maven components";
            case "QUALIFIER_MISMATCH" -> "Version qualifier differs from current version";
            default -> code;
        };
    }

    private static int[] numericParts(String version) {
        String core = version == null ? "" : version.split("-", 2)[0];
        String[] values = core.split("\\.");
        List<Integer> result = new ArrayList<>();
        for (String value : values) {
            try { result.add(Integer.parseInt(value)); }
            catch (NumberFormatException exception) { break; }
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int value(int[] values, int index) { return index < values.length ? values[index] : 0; }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
