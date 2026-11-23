package pl.sca.fix.core.port;

import java.util.Objects;

/** Result of selecting or creating a branch. */
public record GitBranch(String name, String commitSha) {
    public GitBranch {
        name = Objects.requireNonNull(name, "name must not be null").trim();
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        commitSha = commitSha == null || commitSha.isBlank() ? null : commitSha.trim();
    }
}
