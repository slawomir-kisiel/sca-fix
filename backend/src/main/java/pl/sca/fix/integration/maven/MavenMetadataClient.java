package pl.sca.fix.integration.maven;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pl.sca.fix.config.MavenRepositoryProperties;

@Component
public class MavenMetadataClient {
    private static final Logger log = LoggerFactory.getLogger(MavenMetadataClient.class);
    private final HttpClient httpClient;
    private final MavenRepositoryProperties properties;
    private final Map<String, HttpClient> proxiedClients = new ConcurrentHashMap<>();
    private final Map<String, Credentials> settingsServers;
    private final ProxySettings defaultProxySettings;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> unavailableUntil = new ConcurrentHashMap<>();
    private final Map<String, Integer> transientFailures = new ConcurrentHashMap<>();

    public MavenMetadataClient(MavenRepositoryProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout()).build();
        this.settingsServers = resolveSettingsServers();
        this.defaultProxySettings = resolveDefaultProxySettings();
    }

    public MavenVersionLookup findVersions(String groupId, String artifactId) {
        return findVersions(groupId, artifactId, null);
    }

    public MavenVersionLookup findVersions(String groupId, String artifactId, ProxySettings proxySettings) {
        return findVersions(groupId, artifactId, proxySettings, properties.getRepositories());
    }

    public MavenVersionLookup findVersions(String groupId, String artifactId,
                                           ProxySettings proxySettings,
                                           List<MavenRepositoryProperties.Repository> configuredRepositories) {
        ProxySettings effectiveProxy = proxySettings != null ? proxySettings : defaultProxySettings;
        List<MavenVersionLookup.RemoteVersion> versions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (var repository : mergeRepositories(configuredRepositories)) {
            if (repository.getUrl() == null || repository.getUrl().isBlank()) continue;
            String repositoryKey = repositoryKey(repository);
            if (isBlocked(repositoryKey)) {
                warnings.add(repository.getId() + ": REPOSITORY_TEMPORARILY_BLOCKED");
                log.info("Skipping temporarily blocked Maven repository id={} url={}", repository.getId(), safeUrl(repository.getUrl()));
                continue;
            }
            String cacheKey = repositoryKey + ":" + groupId + ":" + artifactId + ":" + proxyCacheKey(effectiveProxy);
            try {
                var cached = cache.get(cacheKey);
                if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                    versions.addAll(cached.versions);
                    continue;
                }
                var loaded = load(repository, groupId, artifactId, effectiveProxy);
                markSuccess(repositoryKey);
                cache.put(cacheKey, new CacheEntry(loaded, System.currentTimeMillis() + ttlMillis()));
                versions.addAll(loaded);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                warnings.add(repository.getId() + ": REQUEST_INTERRUPTED");
            } catch (RepositoryAccessException exception) {
                handleFailure(repository, repositoryKey, exception, warnings);
            } catch (Exception exception) {
                warnings.add(repository.getId() + ": " + exception.getMessage());
            }
        }
        return new MavenVersionLookup(versions, warnings);
    }

    private List<MavenVersionLookup.RemoteVersion> load(MavenRepositoryProperties.Repository repository,
                                                         String groupId, String artifactId,
                                                         ProxySettings proxySettings) throws Exception {
        String base = repository.getUrl().replaceAll("/+$", "");
        String path = groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        var requestBuilder = HttpRequest.newBuilder(URI.create(base + "/" + path))
                .timeout(readTimeout()).GET();
        Credentials credentialsForRepository = credentials(repository);
        if (credentialsForRepository != null && credentialsForRepository.username() != null
                && !credentialsForRepository.username().isBlank()) {
            String credentials = credentialsForRepository.username() + ":"
                    + (credentialsForRepository.password() == null ? "" : credentialsForRepository.password());
            requestBuilder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        HttpResponse<String> response;
        try {
            response = clientFor(proxySettings).send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new RepositoryAccessException("REQUEST_FAILED", true, exception);
        }
        if (response.statusCode() == 404) return List.of();
        if (response.statusCode() == 401 || response.statusCode() == 403)
            throw new RepositoryAccessException("HTTP_" + response.statusCode(), true, null);
        if (response.statusCode() >= 500)
            throw new RepositoryAccessException("HTTP_" + response.statusCode(), true, null);
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new RepositoryAccessException("HTTP_" + response.statusCode(), false, null);
        return parse(response.body(), repository.getId());
    }

    private HttpClient clientFor(ProxySettings proxySettings) {
        if (proxySettings == null) return httpClient;
        return proxiedClients.computeIfAbsent(proxySettings.cacheKey(), ignored -> buildProxiedClient(proxySettings));
    }

    private HttpClient buildProxiedClient(ProxySettings proxySettings) {
        var builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout())
                .proxy(ProxySelector.of(new InetSocketAddress(proxySettings.host(), proxySettings.port())));
        if (proxySettings.username() != null && proxySettings.password() != null) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return getRequestorType() == RequestorType.PROXY
                            ? new PasswordAuthentication(proxySettings.username(), proxySettings.password().toCharArray())
                            : null;
                }
            });
        }
        return builder.build();
    }

    private List<MavenVersionLookup.RemoteVersion> parse(String xml, String repositoryId) throws Exception {
        var factory = secureXmlFactory();
        var document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList nodes = document.getElementsByTagName("version");
        List<MavenVersionLookup.RemoteVersion> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String version = nodes.item(i).getTextContent().trim();
            if (!version.isBlank()) result.add(new MavenVersionLookup.RemoteVersion(version, repositoryId));
        }
        return result;
    }

    private ProxySettings resolveDefaultProxySettings() {
        if (!properties.isUseM2SettingsProxy()) return null;
        Path settingsPath = settingsXmlPath();
        if (settingsPath == null || !Files.isRegularFile(settingsPath)) return null;
        try {
            ProxySettings settings = readActiveProxyFromSettings(settingsPath);
            if (settings != null) log.info("Using proxy from Maven settings.xml: {}:{}", settings.host(), settings.port());
            return settings;
        } catch (Exception exception) {
            log.warn("Unable to read Maven settings proxy from {}: {}", settingsPath, exception.getMessage());
            return null;
        }
    }

    private Path settingsXmlPath() {
        String configured = properties.getSettingsXmlPath();
        if (configured != null && !configured.isBlank()) return Path.of(configured.trim());
        String userHome = System.getProperty("user.home");
        return userHome == null || userHome.isBlank() ? null : Path.of(userHome, ".m2", "settings.xml");
    }

    private ProxySettings readActiveProxyFromSettings(Path settingsPath) throws Exception {
        var document = secureXmlFactory().newDocumentBuilder().parse(settingsPath.toFile());
        NodeList proxies = document.getElementsByTagName("proxy");
        for (int i = 0; i < proxies.getLength(); i++) {
            Node node = proxies.item(i);
            if (!(node instanceof Element proxy) || !proxyActive(proxy)) continue;
            try {
                return ProxySettings.from(childText(proxy, "host"), parsePort(childText(proxy, "port")),
                        childText(proxy, "username"), childText(proxy, "password"));
            } catch (IllegalArgumentException exception) {
                log.warn("Skipping invalid proxy entry in Maven settings.xml: {}", exception.getMessage());
            }
        }
        return null;
    }

    private boolean proxyActive(Element proxy) {
        String active = childText(proxy, "active");
        return active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
    }

    private Integer parsePort(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value.trim()); }
        catch (NumberFormatException exception) { return null; }
    }

    private String childText(Element node, String field) {
        NodeList values = node.getElementsByTagName(field);
        if (values.getLength() == 0) return null;
        String value = values.item(0).getTextContent();
        return value == null ? null : value.trim();
    }

    private DocumentBuilderFactory secureXmlFactory() throws Exception {
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

    private String proxyCacheKey(ProxySettings proxySettings) {
        return proxySettings == null ? "DIRECT" : proxySettings.cacheKey();
    }

    private long ttlMillis() {
        Duration ttl = properties.getCacheTtl();
        return Math.max(0, ttl == null ? Duration.ofHours(1).toMillis() : ttl.toMillis());
    }

    private List<MavenRepositoryProperties.Repository> mergeRepositories(
            List<MavenRepositoryProperties.Repository> configuredRepositories) {
        Map<String, MavenRepositoryProperties.Repository> result = new java.util.LinkedHashMap<>();
        if (configuredRepositories != null) {
            for (var repository : configuredRepositories) {
                if (repository != null && repository.getUrl() != null && !repository.getUrl().isBlank())
                    result.putIfAbsent(repositoryKey(repository), repository);
            }
        }
        for (var repository : properties.getRepositories()) {
            if (repository != null && repository.getUrl() != null && !repository.getUrl().isBlank())
                result.putIfAbsent(repositoryKey(repository), repository);
        }
        return new ArrayList<>(result.values());
    }

    private String repositoryKey(MavenRepositoryProperties.Repository repository) {
        return repository.getUrl().trim().replaceAll("/+$", "").toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isBlocked(String repositoryKey) {
        Long until = unavailableUntil.get(repositoryKey);
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        unavailableUntil.remove(repositoryKey, until);
        return false;
    }

    private void markSuccess(String repositoryKey) {
        transientFailures.remove(repositoryKey);
        unavailableUntil.remove(repositoryKey);
    }

    private void handleFailure(MavenRepositoryProperties.Repository repository, String repositoryKey,
                               RepositoryAccessException exception, List<String> warnings) {
        int failures = exception.temporarilyUnavailable()
                ? transientFailures.merge(repositoryKey, 1, Integer::sum) : 0;
        boolean block = exception.temporarilyUnavailable()
                && (!exception.isServerFailure() || failures >= 2);
        if (block) {
            long until = System.currentTimeMillis() + blockDurationMillis();
            unavailableUntil.put(repositoryKey, until);
            log.warn("Temporarily blocking Maven repository id={} url={} for {} ms reason={}",
                    repository.getId(), safeUrl(repository.getUrl()), blockDurationMillis(), exception.getMessage());
        }
        warnings.add(repository.getId() + ": " + exception.getMessage());
    }

    private Credentials credentials(MavenRepositoryProperties.Repository repository) {
        if (repository.getUsername() != null && !repository.getUsername().isBlank())
            return new Credentials(repository.getUsername(), repository.getPassword());
        return settingsServers.get(repository.getId());
    }

    private Map<String, Credentials> resolveSettingsServers() {
        Map<String, Credentials> result = new java.util.HashMap<>();
        Path path = settingsXmlPath();
        if (path == null || !Files.isRegularFile(path)) return result;
        try {
            var document = secureXmlFactory().newDocumentBuilder().parse(path.toFile());
            NodeList servers = document.getElementsByTagName("server");
            for (int i = 0; i < servers.getLength(); i++) {
                if (!(servers.item(i) instanceof Element server)) continue;
                String id = childText(server, "id");
                if (id == null || id.isBlank()) continue;
                result.put(id, new Credentials(childText(server, "username"), childText(server, "password")));
            }
        } catch (Exception exception) {
            log.warn("Unable to read Maven repository credentials from {}: {}", path, exception.getMessage());
        }
        return result;
    }

    private Duration connectTimeout() {
        return positive(properties.getConnectTimeout(), Duration.ofSeconds(10));
    }

    private Duration readTimeout() {
        return positive(properties.getReadTimeout(), Duration.ofSeconds(15));
    }

    private long blockDurationMillis() {
        return positive(properties.getUnavailableBlockDuration(), Duration.ofMinutes(10)).toMillis();
    }

    private Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private String safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            String authority = uri.getRawAuthority();
            if (authority != null) {
                int credentialsEnd = authority.indexOf('@');
                if (credentialsEnd >= 0) authority = authority.substring(credentialsEnd + 1);
            }
            return (uri.getScheme() == null ? "" : uri.getScheme() + "://")
                    + (authority == null ? "" : authority)
                    + (uri.getRawPath() == null ? "" : uri.getRawPath());
        } catch (Exception ignored) {
            return "<invalid-url>";
        }
    }

    public record ProxySettings(String host, Integer port, String username, String password) {
        public static ProxySettings from(String host, Integer port, String username, String password) {
            boolean hostPresent = host != null && !host.isBlank();
            boolean portPresent = port != null;
            boolean usernamePresent = username != null && !username.isBlank();
            boolean passwordPresent = password != null && !password.isBlank();
            if (!hostPresent && !portPresent && !usernamePresent && !passwordPresent) return null;
            if (!hostPresent || !portPresent) throw new IllegalArgumentException("Both proxyHost and proxyPort must be provided");
            if (port < 1 || port > 65535) throw new IllegalArgumentException("proxyPort must be between 1 and 65535");
            if (passwordPresent && !usernamePresent) throw new IllegalArgumentException("proxyUsername is required when proxyPassword is provided");
            return new ProxySettings(host.trim(), port, usernamePresent ? username.trim() : null, passwordPresent ? password : null);
        }

        public String cacheKey() {
            return host + ":" + port + ":" + (username == null ? "" : username);
        }
    }

    private record CacheEntry(List<MavenVersionLookup.RemoteVersion> versions, long expiresAt) { }

    private record Credentials(String username, String password) { }

    private static class RepositoryAccessException extends IOException {
        private final boolean temporarilyUnavailable;
        private final boolean serverFailure;

        private RepositoryAccessException(String message, boolean temporarilyUnavailable, Throwable cause) {
            super(message, cause);
            this.temporarilyUnavailable = temporarilyUnavailable;
            this.serverFailure = message != null && message.startsWith("HTTP_5");
        }

        boolean temporarilyUnavailable() { return temporarilyUnavailable; }
        boolean isServerFailure() { return serverFailure; }
    }
}
