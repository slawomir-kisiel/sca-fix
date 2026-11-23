package pl.sca.fix.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** A Maven project identified within a repository checkout. */
public record ProjectModel(
        ArtifactCoordinate coordinate,
        String name,
        Path pomPath,
        String packaging,
        ArtifactCoordinate parent,
        List<Path> modulePomPaths) {

    public ProjectModel {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        name = name == null || name.isBlank() ? coordinate.artifactId() : name.trim();
        pomPath = repositoryRelative(pomPath, "pomPath");
        packaging = packaging == null || packaging.isBlank() ? "jar" : packaging.trim();
        modulePomPaths = modulePomPaths == null ? List.of()
                : modulePomPaths.stream().map(path -> repositoryRelative(path, "modulePomPath")).toList();
        if (modulePomPaths.stream().distinct().count() != modulePomPaths.size()) {
            throw new IllegalArgumentException("modulePomPaths must not contain duplicates");
        }
    }

    public ProjectModel(ArtifactCoordinate coordinate, Path pomPath) {
        this(coordinate, coordinate == null ? null : coordinate.artifactId(), pomPath, "jar", null, List.of());
    }

    private static Path repositoryRelative(Path path, String field) {
        Objects.requireNonNull(path, field + " must not be null");
        Path normalized = path.normalize();
        if (normalized.isAbsolute() || normalized.toString().isBlank() || normalized.startsWith("..")) {
            throw new IllegalArgumentException(field + " must be a non-empty repository-relative path");
        }
        return normalized;
    }
}
