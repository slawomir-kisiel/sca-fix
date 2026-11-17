package pl.sca.fix.project.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class PomParser {
    public PomDescriptor parse(Path repositoryRoot, Path pomFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(pomFile.toFile());
            Node project = document.getDocumentElement();
            Node parent = child(project, "parent");
            String parentGroupId = text(parent, "groupId");
            String parentArtifactId = text(parent, "artifactId");
            String parentVersion = text(parent, "version");
            String groupId = first(text(project, "groupId"), parentGroupId);
            String version = first(text(project, "version"), parentVersion);
            List<String> modules = new ArrayList<>();
            Node modulesNode = child(project, "modules");
            if (modulesNode != null) {
                NodeList children = modulesNode.getChildNodes();
                for (int index = 0; index < children.getLength(); index++) {
                    Node module = children.item(index);
                    if ("module".equals(localName(module))) modules.add(module.getTextContent().trim());
                }
            }
            String relativePath = repositoryRoot.relativize(pomFile).toString().replace('\\', '/');
            return new PomDescriptor(relativePath, groupId, text(project, "artifactId"), version,
                    first(text(project, "packaging"), "jar"),
                    new PomDescriptor.ParentCoordinates(parentGroupId, parentArtifactId, parentVersion), modules);
        } catch (Exception exception) {
            throw new ProjectDiscoveryException("POM_PARSE_FAILED", "Unable to parse POM: " + pomFile, exception);
        }
    }

    private Node child(Node node, String name) {
        if (node == null) return null;
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (name.equals(localName(child))) return child;
        }
        return null;
    }

    private String text(Node node, String name) {
        Node value = child(node, name);
        return value == null || value.getTextContent().isBlank() ? null : value.getTextContent().trim();
    }

    private String first(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String localName(Node node) { return node == null ? null : node.getLocalName() != null ? node.getLocalName() : node.getNodeName(); }
}
