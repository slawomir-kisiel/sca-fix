package pl.sca.fix.core.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.model.ArtifactCoordinate;
import pl.sca.fix.core.port.FileSystemPort;

/** Parses the native JSON emitted by maven-dependency-plugin. */
public final class JsonDependencyTreeParser {
    private final ObjectMapper mapper;
    private final FileSystemPort fileSystem;

    public JsonDependencyTreeParser() {
        this(new ObjectMapper(), new NioFileSystemAdapter());
    }

    public JsonDependencyTreeParser(ObjectMapper mapper, FileSystemPort fileSystem) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem must not be null");
    }

    public DependencyTree parse(Path input) {
        return parse(input, null);
    }

    public DependencyTree parse(Path input, ArtifactCoordinate projectCoordinate) {
        Objects.requireNonNull(input, "input must not be null");
        try {
            JsonNode document = mapper.readTree(fileSystem.readBytes(input));
            if (document == null || document.isNull() || document.isMissingNode()) {
                throw failure(input, "JSON is empty");
            }
            JsonNode project = document.isObject() ? document.get("project") : null;
            ArtifactCoordinate rootCoordinate;
            if (projectCoordinate != null) {
                rootCoordinate = projectCoordinate;
            } else if (project != null && !project.isNull()) {
                rootCoordinate = coordinate(project, input, "project");
            } else {
                rootCoordinate = coordinate(document, input, "project");
            }
            JsonNode dependencies = document.isObject() ? document.get("dependencies") : document;
            if (dependencies == null && project == null && document.isObject()) {
                dependencies = document.get("children");
            }
            if (dependencies == null || dependencies.isNull()) dependencies = document.get("children");
            List<DependencyTreeNode> children = new ArrayList<>();
            if (dependencies != null && !dependencies.isMissingNode()) {
                if (!dependencies.isArray()) throw failure(input, "dependencies must be an array");
                for (int index = 0; index < dependencies.size(); index++) {
                    children.add(parseNode(dependencies.get(index), String.valueOf(index), input));
                }
            }
            DependencyTreeNode root = new DependencyTreeNode(null, rootCoordinate, "", false, children);
            return new DependencyTree(root, new DependencyReferenceIndexBuilder().build(root));
        } catch (DependencyTreeParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(input, "Unable to parse JSON", exception);
        }
    }

    private DependencyTreeNode parseNode(JsonNode node, String nodeId, Path input) {
        if (node == null || !node.isObject()) throw failure(input, "dependency node must be an object");
        ArtifactCoordinate coordinate = coordinate(node, input, "dependency " + nodeId);
        String scope = text(node, "scope", "");
        boolean optional = booleanValue(node.get("optional"), false, input);
        JsonNode childrenNode = node.get("children");
        if (childrenNode == null || childrenNode.isNull()) childrenNode = node.get("dependencies");
        List<DependencyTreeNode> children = new ArrayList<>();
        if (childrenNode != null && !childrenNode.isNull()) {
            if (!childrenNode.isArray()) throw failure(input, "children must be an array for " + nodeId);
            for (int index = 0; index < childrenNode.size(); index++) {
                children.add(parseNode(childrenNode.get(index), nodeId + "." + index, input));
            }
        }
        return new DependencyTreeNode(nodeId, coordinate, scope, optional, children);
    }

    private ArtifactCoordinate coordinate(JsonNode node, Path input, String context) {
        if (node == null || !node.isObject()) throw failure(input, "missing " + context + " object");
        String groupId = text(node, "groupId", null);
        String artifactId = text(node, "artifactId", null);
        String version = text(node, "version", null);
        if (groupId == null || artifactId == null || version == null) {
            throw failure(input, "missing groupId, artifactId or version for " + context);
        }
        String type = text(node, "type", text(node, "packaging", "jar"));
        String classifier = text(node, "classifier", "");
        try { return new ArtifactCoordinate(groupId, artifactId, version, type, classifier); }
        catch (IllegalArgumentException exception) { throw failure(input, "invalid coordinate for " + context, exception); }
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private static boolean booleanValue(JsonNode value, boolean defaultValue, Path input) {
        if (value == null || value.isNull()) return defaultValue;
        if (value.isBoolean()) return value.booleanValue();
        if (value.isTextual() && ("true".equalsIgnoreCase(value.textValue()) || "false".equalsIgnoreCase(value.textValue()))) {
            return Boolean.parseBoolean(value.textValue());
        }
        throw failure(input, "optional must be boolean");
    }

    private static DependencyTreeParseException failure(Path input, String message) {
        return new DependencyTreeParseException(input + ": " + message);
    }

    private static DependencyTreeParseException failure(Path input, String message, Throwable cause) {
        return new DependencyTreeParseException(input + ": " + message, cause);
    }
}
