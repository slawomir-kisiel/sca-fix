package pl.sca.fix.core.maven;

import java.util.List;

/** A grouped reason for rejecting candidate versions. */
public record VersionRejection(String reasonCode, String reason, List<String> versions) {
    public VersionRejection {
        versions = List.copyOf(versions == null ? List.of() : versions);
    }
}
