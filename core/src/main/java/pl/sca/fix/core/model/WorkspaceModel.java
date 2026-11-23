package pl.sca.fix.core.model;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable input context for one pipeline workspace. */
public record WorkspaceModel(String workspaceId, Path workspaceDirectory,
                             RepositoryModel repository, ProjectScope projectScope) {
    public WorkspaceModel {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        workspaceId = workspaceId.trim();
        if (workspaceId.isEmpty()) throw new IllegalArgumentException("workspaceId must not be blank");
        Objects.requireNonNull(workspaceDirectory, "workspaceDirectory must not be null");
        workspaceDirectory = workspaceDirectory.toAbsolutePath().normalize();
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(projectScope, "projectScope must not be null");
    }
}
