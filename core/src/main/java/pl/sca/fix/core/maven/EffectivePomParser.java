package pl.sca.fix.core.maven;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import pl.sca.fix.core.port.FileSystemPort;

/** Parses only the effective-POM fields required by the core use cases. */
public final class EffectivePomParser {
    private final FileSystemPort fileSystem;

    public EffectivePomParser(FileSystemPort fileSystem) {
        this.fileSystem = fileSystem;
    }

    public Parsed parse(java.nio.file.Path path) {
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
                @Override public void warning(SAXParseException e) throws SAXParseException { throw e; }
                @Override public void error(SAXParseException e) throws SAXParseException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
            });
            Document document = builder.parse(new ByteArrayInputStream(fileSystem.readBytes(path)));
            Element project = document.getDocumentElement();
            if (project == null || !"project".equals(name(project))) throw error(path, "root is not <project>");
            Element parent = child(project, "parent");
            EffectivePom.Dependency parentValue = parent == null ? null : dependency(parent, null);
            return new Parsed(
                    text(project, "groupId"), text(project, "artifactId"), text(project, "version"),
                    parentValue, dependencies(child(project, "dependencies")),
                    dependencies(child(child(project, "dependencyManagement"), "dependencies")),
                    repositories(child(project, "repositories")), document);
        } catch (EffectivePomParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EffectivePomParseException("Unable to parse effective POM " + path, exception);
        }
    }

    public EffectivePom parseModel(java.nio.file.Path path, EffectivePom.Metadata metadata,
                                   List<EffectivePom.PomSource> pomChain) {
        Parsed parsed = parse(path);
        return new EffectivePom(metadata, parsed.groupId(), parsed.artifactId(), parsed.version(),
                parsed.parent(), parsed.dependencies(), parsed.dependencyManagement(), pomChain, parsed.repositories());
    }

    private List<EffectivePom.Dependency> dependencies(Element container) {
        if (container == null) return List.of();
        List<EffectivePom.Dependency> result = new ArrayList<>();
        NodeList children = container.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && "dependency".equals(name(element))) {
                result.add(dependency(element, versionLocation(element, child(element, "version"))));
            }
        }
        return List.copyOf(result);
    }

    private List<MavenRepositorySpec> repositories(Element container) {
        if (container == null) return List.of();
        List<MavenRepositorySpec> result = new ArrayList<>();
        NodeList children = container.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (!(node instanceof Element repository) || !"repository".equals(name(repository))) continue;
            String url = text(repository, "url");
            if (url == null || url.isBlank()) continue;
            Element releases = child(repository, "releases");
            Element snapshots = child(repository, "snapshots");
            result.add(new MavenRepositorySpec(text(repository, "id"), url,
                    enabled(releases, true), enabled(snapshots, false)));
        }
        return List.copyOf(result);
    }

    private static boolean enabled(Element section, boolean fallback) {
        if (section == null) return fallback;
        String value = text(section, "enabled");
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private EffectivePom.Dependency dependency(Element element, EffectivePom.VersionLocation location) {
        Element versionElement = child(element, "version");
        EffectivePom.VersionLocation actual = location == null ? versionLocation(element, versionElement) : location;
        return new EffectivePom.Dependency(text(element, "groupId"), text(element, "artifactId"),
                text(element, "version"), text(element, "scope"), text(element, "type"),
                text(element, "classifier"), actual);
    }

    private EffectivePom.VersionLocation versionLocation(Element parent, Element version) {
        String comment = adjacentComment(parent, version);
        if (comment == null) return null;
        String normalized = comment.replace("<!--", "").replace("-->", "").trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(.+?)(?:,\\s*line\\s+(\\d+))?\\s*$").matcher(normalized);
        if (!matcher.matches()) return null;
        Integer line = matcher.group(2) == null ? null : Integer.valueOf(matcher.group(2));
        return new EffectivePom.VersionLocation(matcher.group(1), line);
    }

    private static String adjacentComment(Element parent, Element version) {
        if (version == null) return null;
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) != version) continue;
            for (int offset : new int[]{1, -1}) {
                int candidate = index + offset;
                while (candidate >= 0 && candidate < nodes.getLength()) {
                    Node sibling = nodes.item(candidate);
                    if (sibling instanceof Comment comment) return comment.getData();
                    if (sibling.getNodeType() != Node.TEXT_NODE || !sibling.getTextContent().isBlank()) break;
                    candidate += offset;
                }
            }
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element child = child(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && name.equals(name(element))) return element;
        }
        return null;
    }

    private static String name(Element element) {
        String local = element.getLocalName();
        return local == null ? element.getNodeName() : local;
    }

    private static EffectivePomParseException error(java.nio.file.Path path, String message) {
        return new EffectivePomParseException(path + ": " + message);
    }

    public record Parsed(String groupId, String artifactId, String version,
                         EffectivePom.Dependency parent, List<EffectivePom.Dependency> dependencies,
                         List<EffectivePom.Dependency> dependencyManagement,
                         List<MavenRepositorySpec> repositories, Document document) {
        public Parsed {
            dependencies = List.copyOf(dependencies);
            dependencyManagement = List.copyOf(dependencyManagement);
            repositories = List.copyOf(repositories);
        }
    }
}
