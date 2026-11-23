package pl.sca.fix.core.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Discovery input prepared by CLI or Maven Plugin adapter. */
public record ProjectDiscoveryRequest(Path repositoryRoot, List<String> selectors,
                                      boolean expandModules) {
    public ProjectDiscoveryRequest {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot must not be null")
                .toAbsolutePath().normalize();
        selectors = selectors == null ? List.of() : List.copyOf(selectors);
    }

    public static ProjectDiscoveryRequest cli(Path repositoryRoot, List<String> selectors) {
        return new ProjectDiscoveryRequest(repositoryRoot, selectors == null || selectors.isEmpty()
                ? List.of("*") : selectors, false);
    }

    public static ProjectDiscoveryRequest mavenPlugin(Path repositoryRoot, String currentProject) {
        return new ProjectDiscoveryRequest(repositoryRoot, List.of(currentProject), true);
    }
}
