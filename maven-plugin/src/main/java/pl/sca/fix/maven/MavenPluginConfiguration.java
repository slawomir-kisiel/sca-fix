package pl.sca.fix.maven;

import java.nio.file.Path;
import java.util.List;
import pl.sca.fix.core.report.UpdateLevel;

/** Maven-facing configuration mapped to the shared core pipeline. */
public record MavenPluginConfiguration(Path repository, Path workspaceRoot, Path pomsRoot,
                                       List<String> projects, String reportSource,
                                       Path reportInput, Path reportOutput, UpdateLevel updateLevel,
                                       String ambiguousStrategy, String versionInfoTtl,
                                       boolean ignoreLocalChanges, boolean apply, boolean commit,
                                       String pushBranch, boolean overrideReview, boolean verbose) {
    public MavenPluginConfiguration {
        projects = List.copyOf(projects == null || projects.isEmpty() ? List.of("*") : projects);
        updateLevel = updateLevel == null ? UpdateLevel.MINOR : updateLevel;
        ambiguousStrategy = ambiguousStrategy == null ? "COMMON_SOURCE" : ambiguousStrategy;
        versionInfoTtl = versionInfoTtl == null ? "R1d,S10m" : versionInfoTtl;
    }
}
