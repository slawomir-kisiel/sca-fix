package pl.sca.fix.core.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.maven.DependencyTree;
import pl.sca.fix.core.maven.DependencyTreeNode;

public final class CorrelationService {
    public CorrelationResult correlate(CanonicalScanReport report, List<ProjectTree> analysisProjects,
                                       UpdateLevel updateLevel) {
        if (report == null) throw new IllegalArgumentException("Report is required");
        if (updateLevel == null) updateLevel = UpdateLevel.MINOR;
        List<CorrelationEntry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (VulnerableLibrary library : report.vulnerableLibraries()) {
            List<DependencyTreeOccurrence> occurrences = new ArrayList<>();
            for (ProjectTree project : analysisProjects == null ? List.<ProjectTree>of() : analysisProjects) {
                collect(library, project, updateLevel, occurrences, warnings);
            }
            occurrences.sort(Comparator.comparing(DependencyTreeOccurrence::projectId)
                    .thenComparing(DependencyTreeOccurrence::pathId));
            CorrelationStatus status = occurrences.isEmpty() ? CorrelationStatus.ORPHAN
                    : occurrences.size() == 1 ? CorrelationStatus.MATCHED : CorrelationStatus.AMBIGUOUS;
            entries.add(new CorrelationEntry(key(library), status, occurrences, List.of()));
        }
        return new CorrelationResult(1, entries, warnings.stream().distinct().toList(), List.of());
    }

    private void collect(VulnerableLibrary library, ProjectTree project, UpdateLevel level,
                         List<DependencyTreeOccurrence> occurrences, List<String> warnings) {
        String key = library.groupId() + ':' + library.artifactId();
        List<String> nodeIds = project.tree().references().getOrDefault(key, List.of());
        Map<String, DependencyTreeNode> nodes = index(project.tree());
        for (String nodeId : nodeIds) {
            DependencyTreeNode node = nodes.get(nodeId);
            if (node == null) { warnings.add("Invalid dependency tree node: " + project.projectId() + '/' + nodeId); continue; }
            if (!VersionCompatibility.matches(library.version(), node.coordinate().version(), level)) continue;
            occurrences.add(new DependencyTreeOccurrence(project.projectId(), project.projectId() + '/' + nodeId,
                    nodeId, node.depth(), previous(nodeId), node.coordinate().version(), "DEPENDENCY"));
        }
    }

    private static Map<String, DependencyTreeNode> index(DependencyTree tree) {
        Map<String, DependencyTreeNode> result = new LinkedHashMap<>();
        for (DependencyTreeNode child : tree.root().children()) collect(child, result);
        return result;
    }

    private static void collect(DependencyTreeNode node, Map<String, DependencyTreeNode> index) {
        index.put(node.nodeId(), node);
        node.children().forEach(child -> collect(child, index));
    }

    private static String previous(String nodeId) {
        int separator = nodeId.lastIndexOf('.');
        return separator < 0 ? null : nodeId.substring(0, separator);
    }

    private static String key(VulnerableLibrary library) {
        return library.groupId() + ':' + library.artifactId() + ':' + library.version();
    }
}
