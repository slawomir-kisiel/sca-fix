package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record GitCloneResult(Path destination, String branch, String commitSha, Duration duration) {
    public GitCloneResult {
        Objects.requireNonNull(destination, "destination must not be null");
        destination = destination.toAbsolutePath().normalize();
        branch = branch == null || branch.isBlank() ? null : branch.trim();
        commitSha = commitSha == null || commitSha.isBlank() ? null : commitSha.trim();
        Objects.requireNonNull(duration, "duration must not be null");
    }
}

