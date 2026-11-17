package pl.sca.fix.integration.maven;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.NodeList;
import pl.sca.fix.config.MavenRepositoryProperties;

@Component
public class MavenMetadataClient {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final MavenRepositoryProperties properties;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public MavenMetadataClient(MavenRepositoryProperties properties) {
        this.properties = properties;
    }

    public MavenVersionLookup findVersions(String groupId, String artifactId) {
        List<MavenVersionLookup.RemoteVersion> versions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (var repository : properties.getRepositories()) {
            if (repository.getUrl() == null || repository.getUrl().isBlank()) continue;
            String cacheKey = repository.getId() + ":" + groupId + ":" + artifactId;
            try {
                var cached = cache.get(cacheKey);
                if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                    versions.addAll(cached.versions);
                    continue;
                }
                var loaded = load(repository, groupId, artifactId);
                cache.put(cacheKey, new CacheEntry(loaded, System.currentTimeMillis() + ttlMillis()));
                versions.addAll(loaded);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                warnings.add(repository.getId() + ": REQUEST_INTERRUPTED");
            } catch (Exception exception) {
                warnings.add(repository.getId() + ": " + exception.getMessage());
            }
        }
        return new MavenVersionLookup(versions, warnings);
    }

    private List<MavenVersionLookup.RemoteVersion> load(MavenRepositoryProperties.Repository repository,
                                                         String groupId, String artifactId)
            throws Exception {
        String base = repository.getUrl().replaceAll("/+$", "");
        String path = groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        var requestBuilder = HttpRequest.newBuilder(URI.create(base + "/" + path))
                .timeout(Duration.ofSeconds(15)).GET();
        if (repository.getUsername() != null && !repository.getUsername().isBlank()) {
            String credentials = repository.getUsername() + ":" + (repository.getPassword() == null ? "" : repository.getPassword());
            requestBuilder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) return List.of();
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("HTTP_" + response.statusCode());
        return parse(response.body(), repository.getId());
    }

    private List<MavenVersionLookup.RemoteVersion> parse(String xml, String repositoryId) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList nodes = document.getElementsByTagName("version");
        List<MavenVersionLookup.RemoteVersion> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String version = nodes.item(i).getTextContent().trim();
            if (!version.isBlank()) result.add(new MavenVersionLookup.RemoteVersion(version, repositoryId));
        }
        return result;
    }

    private long ttlMillis() {
        Duration ttl = properties.getCacheTtl();
        return Math.max(0, ttl == null ? Duration.ofHours(1).toMillis() : ttl.toMillis());
    }

    private record CacheEntry(List<MavenVersionLookup.RemoteVersion> versions, long expiresAt) { }
}
