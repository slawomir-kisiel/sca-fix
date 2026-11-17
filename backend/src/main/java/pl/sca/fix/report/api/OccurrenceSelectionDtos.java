package pl.sca.fix.report.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OccurrenceSelectionDtos {
    private OccurrenceSelectionDtos() { }

    public record OccurrenceOptionDto(UUID occurrenceId, UUID projectId, UUID artifactVersionId, UUID treeNodeId,
                                      String projectName, String pomPath, String groupId, String artifactId,
                                      String resolvedVersion, String definitionSource,
                                      boolean sourcesAvailable, boolean selected, List<OccurrencePathNodeDto> path) { }

    public record OccurrencePathNodeDto(UUID treeNodeId, UUID artifactVersionId, String groupId,
                                        String artifactId, String version, String relationType,
                                        boolean locallyManaged, boolean vulnerable) { }

    public record SelectOccurrenceRequest(UUID occurrenceId) { }

    public record OccurrenceSelectionDto(UUID findingId, UUID occurrenceId, UUID repositoryId,
                                         Instant selectedAt) { }
}
