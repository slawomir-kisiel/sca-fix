package pl.sca.fix.core.maven;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.model.ArtifactCoordinate;
import pl.sca.fix.core.port.FileSystemPort;

/** Reads the compact text-plus-index dependency tree file. */
public final class DependencyTreeFileReader {
    private final FileSystemPort fileSystem;
    private final ObjectMapper mapper;

    public DependencyTreeFileReader(FileSystemPort fileSystem) {
        this(fileSystem, new ObjectMapper());
    }

    public DependencyTreeFileReader(FileSystemPort fileSystem, ObjectMapper mapper) {
        this.fileSystem = fileSystem;
        this.mapper = mapper;
    }

    public DependencyTree read(Path path) {
        try {
            String content = new String(fileSystem.readBytes(path), StandardCharsets.UTF_8);
            String separator = "\n----\n";
            int first = content.indexOf(separator);
            if (first < 0 || first != content.lastIndexOf(separator)) {
                throw error(path, "file must contain exactly one separator ----");
            }
            String text = content.substring(0, first);
            String indexJson = content.substring(first + separator.length()).trim();
            if (text.isBlank() || indexJson.isBlank()) throw error(path, "tree or index is empty");
            Map<String, List<String>> references = mapper.readValue(indexJson,
                    new TypeReference<LinkedHashMap<String, List<String>>>() { });
            DependencyTreeNode root = readTree(text, path);
            validateReferences(references, root, path);
            Map<String, List<String>> expected = new DependencyReferenceIndexBuilder().build(root);
            if (!expected.equals(references)) throw error(path, "reference index does not match text tree");
            return new DependencyTree(root, references);
        } catch (DependencyTreeParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DependencyTreeParseException(path + ": unable to read dependency tree file", exception);
        }
    }

    private DependencyTreeNode readTree(String text, Path path) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        if (lines.length == 0 || lines[0].isBlank()) throw error(path, "missing root");
        ArtifactCoordinate rootCoordinate = coordinate(lines[0], false, path);
        List<MutableNode> rootChildren = new ArrayList<>();
        List<MutableNode> parents = new ArrayList<>();
        int previousDepth = 0;
        for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            if (line.isEmpty()) continue;
            int depth = leadingTabs(line);
            if (depth == 0 || depth > previousDepth + 1) {
                throw error(path, "invalid indentation at line " + (lineIndex + 1));
            }
            String value = line.substring(depth);
            ArtifactCoordinate coordinate = coordinate(value, true, path);
            MutableNode current;
            if (depth == 1) {
                current = new MutableNode(String.valueOf(rootChildren.size()), coordinate, scope(value));
                rootChildren.add(current);
                parents.clear();
                parents.add(current);
            } else {
                if (parents.size() < depth - 1) throw error(path, "missing parent at line " + (lineIndex + 1));
                while (parents.size() > depth - 1) parents.remove(parents.size() - 1);
                MutableNode parent = parents.get(parents.size() - 1);
                current = new MutableNode(parent.nodeId + "." + parent.children.size(), coordinate, scope(value));
                parent.children.add(current);
                parents.add(current);
            }
            previousDepth = depth;
        }
        return new DependencyTreeNode(null, rootCoordinate, "", false,
                rootChildren.stream().map(DependencyTreeFileReader::immutable).toList());
    }

    private static DependencyTreeNode immutable(MutableNode node) {
        return new DependencyTreeNode(node.nodeId, node.coordinate, node.scope,
                false, node.children.stream().map(DependencyTreeFileReader::immutable).toList());
    }

    private static final class MutableNode {
        private final String nodeId;
        private final ArtifactCoordinate coordinate;
        private final String scope;
        private final List<MutableNode> children = new ArrayList<>();

        private MutableNode(String nodeId, ArtifactCoordinate coordinate, String scope) {
            this.nodeId = nodeId;
            this.coordinate = coordinate;
            this.scope = scope;
        }
    }

    private ArtifactCoordinate coordinate(String line, boolean dependency, Path path) {
        String[] fields = line.split(":", -1);
        int expected = dependency ? 5 : 4;
        if (fields.length != expected && !(dependency && fields.length == 6)) {
            throw error(path, "invalid coordinate: " + line);
        }
        String type = fields[2];
        String classifier = fields.length == 6 ? fields[3] : "";
        String version = fields.length == 6 ? fields[4] : fields[3];
        return new ArtifactCoordinate(fields[0], fields[1], version, type, classifier);
    }

    private static String scope(String line) {
        String[] fields = line.split(":", -1);
        return fields.length == 6 ? fields[5] : fields[4];
    }

    private static int leadingTabs(String line) {
        int result = 0;
        while (result < line.length() && line.charAt(result) == '\t') result++;
        return result;
    }

    private static void validateReferences(Map<String, List<String>> references,
                                            DependencyTreeNode root, Path path) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        collectIds(root, ids);
        references.forEach((key, values) -> values.forEach(id -> {
            if (!ids.contains(id)) throw error(path, "reference " + key + " points to unknown node " + id);
        }));
    }

    private static void collectIds(DependencyTreeNode node, java.util.Set<String> ids) {
        for (DependencyTreeNode child : node.children()) {
            ids.add(child.nodeId());
            collectIds(child, ids);
        }
    }

    private static DependencyTreeParseException error(Path path, String message) {
        return new DependencyTreeParseException(path + ": " + message);
    }
}
