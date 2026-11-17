package pl.sca.fix.integration.maven;

import java.util.List;

public record MavenVersionLookup(List<RemoteVersion> versions, List<String> warnings) {
    public record RemoteVersion(String version, String repositoryId) { }
}
