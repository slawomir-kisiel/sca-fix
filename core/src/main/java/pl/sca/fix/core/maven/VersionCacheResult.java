package pl.sca.fix.core.maven;

import java.util.List;

/** Result of an aggregated version lookup, including non-fatal repository warnings. */
public record VersionCacheResult(List<VersionCacheDocument.VersionEntry> versions,
                                 List<String> warnings, boolean fromCache) {
    public VersionCacheResult {
        versions = List.copyOf(versions == null ? List.of() : versions);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
