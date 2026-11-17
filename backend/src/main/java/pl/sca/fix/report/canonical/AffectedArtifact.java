package pl.sca.fix.report.canonical;

public record AffectedArtifact(String purl, String groupId, String artifactId, String version,
                               String fileName, String filePath, String sha1, String sha256) { }
