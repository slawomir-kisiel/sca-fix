package pl.sca.fix.core.maven;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Persistent versions.json document. Secrets are deliberately not part of this model. */
public record VersionCacheDocument(int schemaVersion, String groupId, String artifactId,
                                   List<RepositoryState> repositories) {
    public VersionCacheDocument {
        groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        artifactId = Objects.requireNonNull(artifactId, "artifactId must not be null");
        repositories = List.copyOf(Objects.requireNonNullElse(repositories, List.of()));
    }

    public record RepositoryState(String url, String status, Instant fetchedAt, Instant releaseExpiresAt,
                                  Instant snapshotExpiresAt, Instant blockedUntil, List<String> versions,
                                  String error) {
        public RepositoryState {
            versions = List.copyOf(Objects.requireNonNullElse(versions, List.of()));
        }
    }

    public record VersionEntry(String version, List<String> repositoryUrls, String kind) {
        public VersionEntry {
            repositoryUrls = List.copyOf(Objects.requireNonNullElse(repositoryUrls, List.of()));
        }
    }
}
