package pl.sca.fix.core.report;

import java.nio.file.Path;
import java.util.Objects;
import pl.sca.fix.core.maven.DependencyTree;

public record ProjectTree(String projectId, Path pomPath, DependencyTree tree) {
    public ProjectTree {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank");
        Objects.requireNonNull(tree, "tree must not be null");
    }
}
