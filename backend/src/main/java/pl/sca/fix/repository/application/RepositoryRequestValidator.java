package pl.sca.fix.repository.application;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;

public final class RepositoryRequestValidator {
    private static final Set<String> GIT_SCHEMES = Set.of("https", "http", "ssh", "git", "file");

    private RepositoryRequestValidator() { }

    public static void validateClonePath(String clonePath) {
        if (clonePath == null || clonePath.isBlank()) {
            throw new IllegalArgumentException("clonePath is required");
        }
        try {
            Path.of(clonePath.trim());
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("clonePath is not a valid filesystem path", exception);
        }
    }

    public static void validateRepositoryUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException("repositoryUrl is required");
        }
        URI uri;
        try {
            uri = URI.create(repositoryUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("repositoryUrl is not a valid URI", exception);
        }
        if (uri.getScheme() == null || !GIT_SCHEMES.contains(uri.getScheme().toLowerCase())) {
            throw new IllegalArgumentException("repositoryUrl must use a supported Git scheme");
        }
        if (!"file".equalsIgnoreCase(uri.getScheme()) && uri.getHost() == null) {
            throw new IllegalArgumentException("repositoryUrl must contain a host");
        }
    }
}
