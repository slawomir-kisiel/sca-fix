package pl.sca.fix.core.plan;

import java.nio.file.Path;
import java.util.Map;

/** Input for one deterministic, in-memory POM change. */
public record PomEditRequest(Path pomPath, String strategy, String groupId, String artifactId,
                             String currentVersion, String targetVersion, String propertyName,
                             String scope, String type, String classifier, String operation,
                             Map<String, String> details, String expectedSha256) {
    public PomEditRequest {
        if (pomPath == null) throw new IllegalArgumentException("pomPath must not be null");
        if (blank(strategy)) throw new IllegalArgumentException("strategy must not be blank");
        if (blank(groupId)) throw new IllegalArgumentException("groupId must not be blank");
        if (blank(artifactId)) throw new IllegalArgumentException("artifactId must not be blank");
        details = details == null ? Map.of() : Map.copyOf(details);
        operation = operation == null ? "ADD_OR_UPDATE" : operation;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
