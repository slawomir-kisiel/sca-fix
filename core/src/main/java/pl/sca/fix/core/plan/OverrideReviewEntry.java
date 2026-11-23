package pl.sca.fix.core.plan;

import java.util.List;

public record OverrideReviewEntry(String projectId, String pomPath, String groupId,
                                  String artifactId, String propertyName, String forcedVersion,
                                  String resolvedVersionBefore, String resolvedVersionAfter,
                                  OverrideReviewStatus status, PlanIssue issue,
                                  List<String> consumers) {
    public OverrideReviewEntry {
        consumers = List.copyOf(consumers == null ? List.of() : consumers);
    }
}
