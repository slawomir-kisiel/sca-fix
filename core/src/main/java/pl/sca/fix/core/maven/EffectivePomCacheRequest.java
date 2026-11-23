package pl.sca.fix.core.maven;

import java.nio.file.Path;
import java.util.Objects;

/** Request for loading or refreshing one effective-POM cache entry. */
public record EffectivePomCacheRequest(Path repositoryRoot, Path pomFile, Path cacheRoot,
                                       boolean forceRefresh) {
    public EffectivePomCacheRequest {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot must not be null")
                .toAbsolutePath().normalize();
        pomFile = Objects.requireNonNull(pomFile, "pomFile must not be null")
                .toAbsolutePath().normalize();
        cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot must not be null")
                .toAbsolutePath().normalize();
        if (!pomFile.startsWith(repositoryRoot)) throw new IllegalArgumentException("pomFile must be inside repositoryRoot");
    }
}
