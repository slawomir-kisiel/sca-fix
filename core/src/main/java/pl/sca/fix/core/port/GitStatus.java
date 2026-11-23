package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Read-only status of a Git checkout. */
public record GitStatus(boolean repository, boolean clean, String commitSha,
                        List<Path> changedFiles, String branch) {
    public GitStatus(boolean repository, boolean clean, String commitSha, List<Path> changedFiles) {
        this(repository, clean, commitSha, changedFiles, null);
    }

    public GitStatus {
        commitSha = commitSha == null || commitSha.isBlank() ? null : commitSha.trim();
        branch = branch == null || branch.isBlank() ? null : branch.trim();
        changedFiles = List.copyOf(Objects.requireNonNullElse(changedFiles, List.of()));
    }
}
