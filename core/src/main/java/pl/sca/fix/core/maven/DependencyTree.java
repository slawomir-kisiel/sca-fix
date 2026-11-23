package pl.sca.fix.core.maven;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Normalized dependency tree with an index of all artifact occurrences. */
public record DependencyTree(DependencyTreeNode root, Map<String, List<String>> references) {
    public DependencyTree {
        Objects.requireNonNull(root, "root must not be null");
        if (references == null) {
            references = Map.of();
        } else {
            java.util.LinkedHashMap<String, List<String>> copy = new java.util.LinkedHashMap<>();
            references.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            references = java.util.Collections.unmodifiableMap(copy);
        }
    }
}
