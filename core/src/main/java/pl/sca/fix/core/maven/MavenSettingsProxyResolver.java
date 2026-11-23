package pl.sca.fix.core.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pl.sca.fix.core.port.VersionLookupRequest.ProxySettings;

/** Reads the first active proxy from Maven settings.xml. */
public final class MavenSettingsProxyResolver {
    public Optional<ProxySettings> resolve() {
        String explicit = System.getProperty("maven.user.settings");
        if (explicit == null || explicit.isBlank()) explicit = System.getenv("MAVEN_USER_SETTINGS");
        if (explicit != null && !explicit.isBlank()) return resolve(Path.of(explicit));
        Optional<ProxySettings> result = resolve(Path.of(System.getProperty("user.home"), ".m2", "settings.xml"));
        if (result.isPresent()) return result;
        String home = System.getProperty("maven.home");
        return home == null || home.isBlank() ? Optional.empty() : resolve(Path.of(home, "conf", "settings.xml"));
    }

    public Optional<ProxySettings> resolve(Path settings) {
        if (settings == null || !Files.isRegularFile(settings)) return Optional.empty();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            NodeList nodes = factory.newDocumentBuilder().parse(settings.toFile()).getElementsByTagName("proxy");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element proxy && active(proxy)) {
                    String host = child(proxy, "host");
                    int port = port(child(proxy, "port"));
                    if (host != null && port > 0) return Optional.of(new ProxySettings(host, port,
                            child(proxy, "username"), child(proxy, "password")));
                }
            }
        } catch (Exception ignored) { }
        return Optional.empty();
    }

    private static boolean active(Element proxy) {
        String active = child(proxy, "active");
        return active == null || Boolean.parseBoolean(active);
    }

    private static String child(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() == 0) return null;
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int port(String value) {
        try { return Integer.parseInt(value == null ? "-1" : value); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
