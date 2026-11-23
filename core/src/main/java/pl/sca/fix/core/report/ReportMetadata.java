package pl.sca.fix.core.report;

import java.time.Instant;

public record ReportMetadata(String source, String formatVersion, String sourceFileName,
                             Instant reportDate, Instant scanDate, String scannerVersion,
                             RepositoryContext repository, ProjectContext project) {
    public record RepositoryContext(String id, String url, String branch) { }
    public record ProjectContext(String name, String groupId, String artifactId, String version) { }
}
