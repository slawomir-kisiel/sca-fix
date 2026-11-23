package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.util.Objects;

/** Request to generate the native JSON dependency tree for exactly one project. */
public record MavenDependencyTreeRequest(Path pomFile, Path outputFile,
                                         String dependencyPluginVersion,
                                         boolean displayAncestors) {
    public MavenDependencyTreeRequest {
        Objects.requireNonNull(pomFile, "pomFile must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");
        dependencyPluginVersion = dependencyPluginVersion == null || dependencyPluginVersion.isBlank()
                ? null : dependencyPluginVersion.trim();
    }
}
