package pl.sca.fix.core.maven;

import java.time.Duration;
import java.util.Objects;

/** Pinned Maven tool configuration used by the core adapter. */
public record MavenToolConfiguration(String executable, boolean windows,
                                     String helpPluginVersion, String dependencyPluginVersion,
                                     Duration timeout) {
    public MavenToolConfiguration {
        executable = requiredSafe(executable, "executable");
        helpPluginVersion = requiredSafe(helpPluginVersion, "helpPluginVersion");
        dependencyPluginVersion = requiredSafe(dependencyPluginVersion, "dependencyPluginVersion");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
    }

    public static MavenToolConfiguration defaults(boolean windows) {
        return new MavenToolConfiguration("mvn", windows, "3.5.1", "3.8.1", Duration.ofMinutes(10));
    }

    private static String requiredSafe(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.matches(".*[\\s;&|<>\"].*")) {
            throw new IllegalArgumentException(field + " must be a safe non-blank value");
        }
        return normalized;
    }
}
