package pl.sca.fix.integration.maven;

public record MavenCoordinate(String groupId, String artifactId, String version, String scope, int depth) { }
