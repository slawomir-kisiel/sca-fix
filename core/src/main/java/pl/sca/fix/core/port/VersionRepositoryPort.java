package pl.sca.fix.core.port;

/** Reads available artifact versions from a Maven repository.
 * Timeouts, authentication failures and unavailable repositories are reported as {@link PortException}. */
public interface VersionRepositoryPort {
    VersionLookupResult findVersions(VersionLookupRequest request) throws PortException;
}
