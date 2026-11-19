package pl.sca.fix.integration.maven;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pl.sca.fix.config.MavenRepositoryProperties;
import pl.sca.fix.project.domain.Project;

/** Reads active dependency repositories from a persisted effective-pom. */
@Component
public class MavenProjectRepositoryResolver {
    private final Path effectivePomRoot;

    public MavenProjectRepositoryResolver(
            @Value("${sca.maven.effective-pom-root:./data/effective-poms}") String effectivePomRoot) {
        this.effectivePomRoot = Path.of(effectivePomRoot).toAbsolutePath().normalize();
    }

    public List<MavenRepositoryProperties.Repository> resolve(Project project) {
        if (project == null || blank(project.getGroupId()) || blank(project.getArtifactId()) || blank(project.getVersion()))
            return List.of();
        Path file = effectivePomRoot.resolve(project.getGroupId()).resolve(project.getArtifactId())
                .resolve(project.getVersion()).resolve("effective-pom.xml").normalize();
        if (!file.startsWith(effectivePomRoot) || !Files.isRegularFile(file)) return List.of();
        try {
            var document = secureFactory().newDocumentBuilder().parse(file.toFile());
            NodeList repositories = document.getElementsByTagName("repositories");
            List<MavenRepositoryProperties.Repository> result = new ArrayList<>();
            for (int i = 0; i < repositories.getLength(); i++) {
                NodeList entries = ((Element) repositories.item(i)).getElementsByTagName("repository");
                for (int j = 0; j < entries.getLength(); j++) {
                    if (entries.item(j) instanceof Element entry) {
                        var repository = repository(entry);
                        if (repository != null) result.add(repository);
                    }
                }
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private MavenRepositoryProperties.Repository repository(Element element) {
        String url = childText(element, "url");
        if (blank(url)) return null;
        var repository = new MavenRepositoryProperties.Repository();
        repository.setId(blank(childText(element, "id")) ? url : childText(element, "id"));
        repository.setUrl(url.trim());
        Element releases = child(element, "releases");
        Element snapshots = child(element, "snapshots");
        repository.setReleases(releases == null || booleanValue(releases, "enabled", true));
        repository.setSnapshots(snapshots != null && booleanValue(snapshots, "enabled", false));
        return repository;
    }

    private boolean booleanValue(Element parent, String name, boolean fallback) {
        String value = childText(parent, name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private Element child(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) return element;
        }
        return null;
    }

    private String childText(Element parent, String name) {
        Element element = child(parent, name);
        return element == null ? null : element.getTextContent().trim();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private DocumentBuilderFactory secureFactory() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }
}
