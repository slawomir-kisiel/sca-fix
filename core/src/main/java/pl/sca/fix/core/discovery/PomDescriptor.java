package pl.sca.fix.core.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Parsed, repository-relative Maven POM descriptor. */
public record PomDescriptor(Path pomPath, String groupId, String artifactId, String version,
                            String packaging, ParentDescriptor parent, List<String> modules) {
    public PomDescriptor {
        pomPath = Objects.requireNonNull(pomPath, "pomPath must not be null").normalize();
        if (pomPath.isAbsolute() || pomPath.startsWith("..")) {
            throw new IllegalArgumentException("pomPath must be repository-relative");
        }
        groupId = blankToNull(groupId);
        artifactId = blankToNull(artifactId);
        version = blankToNull(version);
        packaging = packaging == null || packaging.isBlank() ? "jar" : packaging.trim();
        modules = List.copyOf(Objects.requireNonNull(modules, "modules must not be null"));
    }

    public boolean hasCompleteCoordinates() {
        return groupId != null && artifactId != null && version != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
