package pl.sca.fix.core.port;

import java.time.Duration;
import java.util.Objects;

/** Identifies an artifact whose available Maven versions should be queried. */
public record VersionLookupRequest(String groupId, String artifactId,
                                   String repositoryUrl, Duration timeout,
                                   String username, String password, ProxySettings proxy) {
    public VersionLookupRequest(String groupId, String artifactId, String repositoryUrl, Duration timeout) {
        this(groupId, artifactId, repositoryUrl, timeout, null, null, null);
    }

    public VersionLookupRequest {
        groupId = required(groupId, "groupId");
        artifactId = required(artifactId, "artifactId");
        repositoryUrl = required(repositoryUrl, "repositoryUrl");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        username = blankToNull(username);
        password = blankToNull(password);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    public record ProxySettings(String host, int port, String username, String password) {
        public ProxySettings {
            if (host == null || host.isBlank()) throw new IllegalArgumentException("proxy host must not be blank");
            if (port < 1 || port > 65535) throw new IllegalArgumentException("proxy port out of range");
        }
    }
}
