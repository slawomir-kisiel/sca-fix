package pl.sca.fix.core.port;

import java.util.List;
import java.util.Objects;

/** Available versions returned by a Maven repository adapter. */
public record VersionLookupResult(List<String> versions, String repositoryUrl) {
    public VersionLookupResult {
        versions = List.copyOf(Objects.requireNonNull(versions, "versions must not be null"));
        repositoryUrl = Objects.requireNonNull(repositoryUrl, "repositoryUrl must not be null");
    }
}
