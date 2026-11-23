package pl.sca.fix.core.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.report.UpdateLevel;

class VersionRecommendationServiceTest {
    private final VersionRecommendationService service = new VersionRecommendationService();

    @Test
    void recommendsHighestVersionWithinMinorLineAndGroupsRejections() {
        VersionRecommendation result = service.recommend("g", "a", "1.2.3", entries(
                "1.2.3", "1.3.0", "1.4.2", "2.0.1", "0.9.0", "1.0.0"), UpdateLevel.MINOR);

        assertEquals("1.4.2", result.recommendedVersion());
        assertEquals(List.of("1.4.2", "1.3.0"), result.eligibleVersions());
        assertEquals(List.of("2.0.1"), rejection(result, "MAJOR_CHANGE_NOT_ALLOWED").versions());
        assertEquals(List.of("1.2.3", "1.0.0", "0.9.0"), rejection(result, "NOT_NEWER_THAN_CURRENT").versions());
    }

    @Test
    void patchKeepsMajorAndMinorAndMajorAllowsMajorChange() {
        VersionRecommendation patch = service.recommend("g", "a", "1.2.3",
                entries("1.2.9", "1.3.0", "2.0.0"), UpdateLevel.PATCH);
        assertEquals("1.2.9", patch.recommendedVersion());
        assertEquals(List.of("2.0.0"), rejection(patch, "MAJOR_CHANGE_NOT_ALLOWED").versions());
        assertEquals(List.of("1.3.0"), rejection(patch, "MINOR_CHANGE_NOT_ALLOWED").versions());

        VersionRecommendation major = service.recommend("g", "a", "1.2.3",
                entries("2.0.0", "1.5.0"), UpdateLevel.MAJOR);
        assertEquals("2.0.0", major.recommendedVersion());
    }

    @Test
    void handlesZeroMajorQualifiersAndDuplicateVersions() {
        VersionRecommendation result = service.recommend("g", "a", "0.1.0-SNAPSHOT",
                entries("0.2.0-SNAPSHOT", "0.2.0-SNAPSHOT", "1.0.0", "0.1.0"), UpdateLevel.MINOR);

        assertEquals("0.2.0-SNAPSHOT", result.recommendedVersion());
        assertEquals(1, result.eligibleVersions().size());
        assertTrue(result.rejections().stream().anyMatch(item -> item.reasonCode().equals("QUALIFIER_MISMATCH")));
    }

    @Test
    void returnsEmptyRecommendationWhenNoCandidateIsEligible() {
        VersionRecommendation result = service.recommend("g", "a", "1.2.3",
                entries("1.0.0", "1.2.3"), UpdateLevel.MINOR);
        assertEquals(null, result.recommendedVersion());
        assertEquals(2, result.rejections().get(0).versions().size());
    }

    @Test
    void requiresCandidateQualifierToMatchCurrentVersion() {
        VersionRecommendation release = service.recommend("g", "a", "2.3.3",
                entries("2.4.0-b180830.0438", "2.4.0"), UpdateLevel.MINOR);
        assertEquals("2.4.0", release.recommendedVersion());
        assertEquals(List.of("2.4.0-b180830.0438"),
                rejection(release, "QUALIFIER_MISMATCH").versions());

        VersionRecommendation qualified = service.recommend("g", "a", "2.3.3-SNAPSHOT",
                entries("2.4.0", "2.4.0-SNAPSHOT"), UpdateLevel.MINOR);
        assertEquals("2.4.0-SNAPSHOT", qualified.recommendedVersion());
        assertEquals(List.of("2.4.0"),
                rejection(qualified, "QUALIFIER_MISMATCH").versions());
    }

    private static VersionRejection rejection(VersionRecommendation result, String code) {
        return result.rejections().stream().filter(item -> item.reasonCode().equals(code)).findFirst().orElseThrow();
    }

    private static List<VersionCacheDocument.VersionEntry> entries(String... versions) {
        return java.util.Arrays.stream(versions)
                .map(version -> new VersionCacheDocument.VersionEntry(version, List.of("test"), "RELEASE"))
                .toList();
    }
}
