package pl.sca.fix.maven;

import java.util.Set;

public final class MavenPluginConfigurationValidator {
    public void validate(MavenPluginConfiguration configuration) {
        if (configuration.repository() == null) throw error("REPOSITORY_REQUIRED", "repository");
        if (configuration.apply() && configuration.reportSource() == null && configuration.reportInput() != null)
            throw error("REPORT_CONFIGURATION_INVALID", "reportInput");
        if (!configuration.apply() && configuration.reportSource() == null && !configuration.overrideReview())
            throw error("REPORT_REQUIRED", "reportSource");
        if (configuration.pushBranch() != null && !configuration.commit())
            throw error("PUSH_REQUIRES_COMMIT", "push");
        if (configuration.pushBranch() != null && configuration.ignoreLocalChanges())
            throw error("PUSH_WITH_IGNORED_CHANGES", "push");
        if (!Set.of("file", "generate").contains(configuration.reportSource())
                && configuration.reportSource() != null)
            throw error("INVALID_REPORT_SOURCE", "reportSource");
        if ("file".equals(configuration.reportSource()) && configuration.reportInput() == null)
            throw error("REPORT_INPUT_REQUIRED", "reportInput");
    }

    private static IllegalArgumentException error(String code, String parameter) {
        return new IllegalArgumentException(code + " [" + parameter + "]");
    }
}
