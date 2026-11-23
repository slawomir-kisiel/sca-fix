package pl.sca.fix.core.maven;

import java.util.List;
import pl.sca.fix.core.report.UpdateLevel;

/** Deterministic version recommendation and its audit information. */
public record VersionRecommendation(String groupId, String artifactId,
                                    String currentVersion, UpdateLevel updateLevel,
                                    String recommendedVersion, List<String> eligibleVersions,
                                    List<VersionRejection> rejections) {
    public VersionRecommendation {
        eligibleVersions = List.copyOf(eligibleVersions == null ? List.of() : eligibleVersions);
        rejections = List.copyOf(rejections == null ? List.of() : rejections);
    }
}
