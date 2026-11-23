package pl.sca.fix.core.maven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the reverse index used to find all occurrences of an artifact. */
public final class DependencyReferenceIndexBuilder {
    public Map<String, List<String>> build(DependencyTreeNode root) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (DependencyTreeNode child : root.children()) add(child, result);
        return result;
    }

    private void add(DependencyTreeNode node, Map<String, List<String>> result) {
        result.computeIfAbsent(key(node), ignored -> new ArrayList<>()).add(node.nodeId());
        node.children().forEach(child -> add(child, result));
    }

    private static String key(DependencyTreeNode node) {
        return node.coordinate().groupId() + ":" + node.coordinate().artifactId();
    }
}
