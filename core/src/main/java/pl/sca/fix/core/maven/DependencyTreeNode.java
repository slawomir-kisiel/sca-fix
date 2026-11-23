package pl.sca.fix.core.maven;

import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.model.ArtifactCoordinate;

/** One occurrence in a Maven dependency tree. */
public record DependencyTreeNode(String nodeId, ArtifactCoordinate coordinate,
                                String scope, boolean optional, List<DependencyTreeNode> children) {
    public DependencyTreeNode {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        scope = scope == null ? "" : scope.trim();
        children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
    }

    public int depth() {
        return nodeId == null || nodeId.isBlank() ? 0 : nodeId.split("\\.").length;
    }
}
