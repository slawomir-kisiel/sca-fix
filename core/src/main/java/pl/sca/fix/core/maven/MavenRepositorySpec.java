package pl.sca.fix.core.maven;

import java.util.Objects;

/** Non-secret repository configuration used by the version cache. */
public record MavenRepositorySpec(String id, String url, boolean releases, boolean snapshots,
                                  String username, String password) {
    public MavenRepositorySpec {
        id = id == null || id.isBlank() ? url : id.trim();
        url = required(url, "url").replaceAll("/+$", "");
        username = blankToNull(username);
        password = blankToNull(password);
    }

    public MavenRepositorySpec(String id, String url, boolean releases, boolean snapshots) {
        this(id, url, releases, snapshots, null, null);
    }

    public String key() { return url.toLowerCase(java.util.Locale.ROOT); }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
