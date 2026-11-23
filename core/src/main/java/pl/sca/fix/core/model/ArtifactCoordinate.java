package pl.sca.fix.core.model;

import java.util.Objects;

/** Maven artifact coordinates used in repository and dependency models. */
public record ArtifactCoordinate(
        String groupId,
        String artifactId,
        String version,
        String type,
        String classifier) {

    public ArtifactCoordinate {
        groupId = required(groupId, "groupId");
        artifactId = required(artifactId, "artifactId");
        version = required(version, "version");
        type = type == null || type.isBlank() ? "jar" : type.trim();
        classifier = classifier == null ? "" : classifier.trim();
    }

    public ArtifactCoordinate(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, "jar", "");
    }

    public String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public String gavtc() {
        return groupId + ":" + artifactId + ":" + type + ":" + classifier + ":" + version;
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
