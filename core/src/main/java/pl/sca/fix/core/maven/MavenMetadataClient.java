package pl.sca.fix.core.maven;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.VersionLookupRequest;
import pl.sca.fix.core.port.VersionLookupResult;
import pl.sca.fix.core.port.VersionRepositoryClientPort;

/** HTTP adapter for Maven maven-metadata.xml. */
public final class MavenMetadataClient implements VersionRepositoryClientPort {
    private final HttpClient directClient;

    public MavenMetadataClient() {
        this.directClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public VersionLookupResult findVersions(VersionLookupRequest request) {
        String base = request.repositoryUrl().replaceAll("/+$", "");
        URI uri = URI.create(base + "/" + request.groupId().replace('.', '/') + "/"
                + request.artifactId() + "/maven-metadata.xml");
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(request.timeout()).GET();
        if (request.username() != null) {
            String raw = request.username() + ":" + (request.password() == null ? "" : request.password());
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            HttpResponse<String> response = client(request).send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) return new VersionLookupResult(List.of(), request.repositoryUrl());
            if (response.statusCode() == 401 || response.statusCode() == 403)
                throw new PortException(PortException.Kind.AUTHENTICATION,
                        "Maven repository authentication failed: " + request.repositoryUrl());
            if (response.statusCode() >= 500)
                throw new PortException(PortException.Kind.IO,
                        "Maven repository unavailable HTTP " + response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new PortException(PortException.Kind.IO,
                        "Maven repository returned HTTP " + response.statusCode());
            return new VersionLookupResult(parse(response.body()), request.repositoryUrl());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PortException(PortException.Kind.TIMEOUT, "Maven metadata request interrupted", exception);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new PortException(PortException.Kind.TIMEOUT, "Maven metadata request timed out", exception);
        } catch (java.io.IOException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to fetch Maven metadata", exception);
        }
    }

    private HttpClient client(VersionLookupRequest request) {
        if (request.proxy() == null) return directClient;
        var builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.of(new InetSocketAddress(request.proxy().host(), request.proxy().port())));
        if (request.proxy().username() != null) {
            builder.authenticator(new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(request.proxy().username(),
                            (request.proxy().password() == null ? "" : request.proxy().password()).toCharArray());
                }
            });
        }
        return builder.build();
    }

    private List<String> parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            NodeList nodes = factory.newDocumentBuilder().parse(
                    new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getElementsByTagName("version");
            List<String> versions = new ArrayList<>();
            for (int index = 0; index < nodes.getLength(); index++) {
                String version = nodes.item(index).getTextContent().trim();
                if (!version.isBlank() && !versions.contains(version)) versions.add(version);
            }
            return List.copyOf(versions);
        } catch (Exception exception) {
            throw new PortException(PortException.Kind.IO, "Invalid Maven metadata XML", exception);
        }
    }
}
