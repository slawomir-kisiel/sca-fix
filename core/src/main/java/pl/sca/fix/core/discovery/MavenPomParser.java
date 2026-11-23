package pl.sca.fix.core.discovery;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import pl.sca.fix.core.port.FileSystemPort;

/** Parses direct Maven POM metadata without interpolation or external processes. */
public final class MavenPomParser {
    private final FileSystemPort fileSystem;

    public MavenPomParser(FileSystemPort fileSystem) {
        this.fileSystem = fileSystem;
    }

    public PomDescriptor parse(Path repositoryRoot, Path pomPath) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path absolutePom = root.resolve(pomPath).normalize();
        if (!absolutePom.startsWith(root)) {
            throw new PomParseException("POM path is outside repository: " + pomPath);
        }
        Path relative = root.relativize(absolutePom);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override public void warning(SAXParseException exception) throws SAXParseException { throw exception; }
                @Override public void error(SAXParseException exception) throws SAXParseException { throw exception; }
                @Override public void fatalError(SAXParseException exception) throws SAXParseException { throw exception; }
            });
            Document document = builder.parse(new ByteArrayInputStream(fileSystem.readBytes(absolutePom)));
            Element project = document.getDocumentElement();
            if (project == null || !"project".equals(localName(project))) {
                throw new PomParseException("Root element is not <project>: " + relative);
            }
            Element parent = directChild(project, "parent");
            ParentDescriptor parentDescriptor = parent == null ? null : new ParentDescriptor(
                    text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version"),
                    text(parent, "relativePath"));
            List<String> modules = directChildren(directChild(project, "modules"), "module");
            return new PomDescriptor(relative, text(project, "groupId"), text(project, "artifactId"),
                    text(project, "version"), text(project, "packaging"), parentDescriptor, modules);
        } catch (PomParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PomParseException("Unable to parse POM " + relative, exception);
        }
    }

    private static String text(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element directChild(Element parent, String name) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && name.equals(localName(element))) return element;
        }
        return null;
    }

    private static List<String> directChildren(Element parent, String name) {
        if (parent == null) return List.of();
        List<String> values = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && name.equals(localName(element))) {
                String value = element.getTextContent().trim();
                if (!value.isEmpty()) values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static String localName(Element element) {
        String localName = element.getLocalName();
        return localName == null ? element.getNodeName() : localName;
    }
}
