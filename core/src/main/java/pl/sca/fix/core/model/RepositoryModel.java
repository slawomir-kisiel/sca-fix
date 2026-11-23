package pl.sca.fix.core.model;

import java.nio.file.Path;
import java.util.Objects;

/** Read-only identity and local checkout location of a source repository. */
public record RepositoryModel(Path rootDirectory, String remoteUrl, String baseCommitSha) {
    public RepositoryModel {
        Objects.requireNonNull(rootDirectory, "rootDirectory must not be null");
        rootDirectory = rootDirectory.toAbsolutePath().normalize();
        remoteUrl = blankToNull(remoteUrl);
        baseCommitSha = blankToNull(baseCommitSha);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
