package pl.sca.fix.core.maven;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Serializable, reduced representation of Maven's verbose effective POM. */
public record EffectivePom(
        Metadata metadata,
        String groupId,
        String artifactId,
        String version,
        Dependency parent,
        List<Dependency> dependencies,
        List<Dependency> dependencyManagement,
        List<PomSource> pomChain,
        List<MavenRepositorySpec> repositories) {

    public static final int CACHE_FORMAT_VERSION = 2;

    public EffectivePom {
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        groupId = required(groupId, "groupId");
        artifactId = required(artifactId, "artifactId");
        version = required(version, "version");
        dependencies = immutable(dependencies);
        dependencyManagement = immutable(dependencyManagement);
        pomChain = immutable(pomChain);
        repositories = immutable(repositories);
    }

    public EffectivePom(Metadata metadata, String groupId, String artifactId, String version,
                        Dependency parent, List<Dependency> dependencies,
                        List<Dependency> dependencyManagement, List<PomSource> pomChain) {
        this(metadata, groupId, artifactId, version, parent, dependencies, dependencyManagement,
                pomChain, List.of());
    }

    @JsonIgnore
    public Map<String, List<IndexedDependency>> dependencyIndex() {
        Map<String, List<IndexedDependency>> index = new LinkedHashMap<>();
        dependencies.forEach(dependency -> add(index, "dependencies", dependency));
        dependencyManagement.forEach(dependency -> add(index, "dependencyManagement", dependency));
        index.replaceAll((key, value) -> List.copyOf(value));
        return Collections.unmodifiableMap(index);
    }

    private static void add(Map<String, List<IndexedDependency>> index, String section,
                            Dependency dependency) {
        index.computeIfAbsent(dependency.key(), ignored -> new ArrayList<>())
                .add(new IndexedDependency(section, dependency));
    }

    private static <T> List<T> immutable(List<T> values) {
        return List.copyOf(Objects.requireNonNullElse(values, List.of()));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    public record Metadata(String hash, Instant lastModified, Integer formatVersion) {
        public Metadata(String hash, Instant lastModified) {
            this(hash, lastModified, CACHE_FORMAT_VERSION);
        }

        public Metadata {
            hash = required(hash, "hash");
            Objects.requireNonNull(lastModified, "lastModified must not be null");
        }
    }

    public record VersionLocation(String artifact, Integer line) {
        public VersionLocation {
            artifact = artifact == null || artifact.isBlank() ? null : artifact.trim();
            if (line != null && line < 1) throw new IllegalArgumentException("line must be positive");
        }
    }

    public record Dependency(String groupId, String artifactId, String version, String scope,
                             String type, String classifier, VersionLocation versionLocation) {
        public Dependency {
            groupId = required(groupId, "groupId");
            artifactId = required(artifactId, "artifactId");
            version = version == null || version.isBlank() ? null : version.trim();
            scope = blankToNull(scope);
            type = blankToNull(type);
            classifier = blankToNull(classifier);
        }

        public String key() {
            return groupId + ":" + artifactId;
        }
    }

    public record IndexedDependency(String section, Dependency dependency) { }

    public record PomSource(String groupId, String artifactId, String version, String pomPath,
                            String hash, Instant lastModified, boolean local) {
        public PomSource {
            groupId = required(groupId, "groupId");
            artifactId = required(artifactId, "artifactId");
            version = required(version, "version");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
