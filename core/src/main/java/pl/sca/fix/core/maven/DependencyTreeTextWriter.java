package pl.sca.fix.core.maven;

import java.util.Objects;
import pl.sca.fix.core.model.ArtifactCoordinate;

/** Renders the normalized tree in the compact, indented text format. */
public final class DependencyTreeTextWriter {
    public String render(DependencyTree tree) {
        Objects.requireNonNull(tree, "tree must not be null");
        StringBuilder output = new StringBuilder();
        output.append(rootCoordinate(tree.root().coordinate())).append('\n');
        for (DependencyTreeNode child : tree.root().children()) append(child, output);
        return output.toString();
    }

    private void append(DependencyTreeNode node, StringBuilder output) {
        int depth = node.depth();
        output.append("\t".repeat(depth))
                .append(dependencyCoordinate(node.coordinate(), node.scope())).append('\n');
        node.children().forEach(child -> append(child, output));
    }

    private static String rootCoordinate(ArtifactCoordinate coordinate) {
        return String.join(":", coordinate.groupId(), coordinate.artifactId(), coordinate.type(),
                coordinate.version());
    }

    private static String dependencyCoordinate(ArtifactCoordinate coordinate, String scope) {
        String base = String.join(":", coordinate.groupId(), coordinate.artifactId(), coordinate.type());
        if (!coordinate.classifier().isBlank()) base += ":" + coordinate.classifier();
        return base + ":" + coordinate.version() + ":" + scope;
    }
}
