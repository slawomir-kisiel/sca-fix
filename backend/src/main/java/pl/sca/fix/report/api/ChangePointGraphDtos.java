package pl.sca.fix.report.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.sca.fix.report.domain.UpdateStrategy;

public final class ChangePointGraphDtos {
    private ChangePointGraphDtos() { }
    public record GraphNode(UUID treeNodeId, UUID artifactVersionId, String groupId, String artifactId, String version,
                            boolean locallyManaged, UUID projectId, String analysisStatus, boolean selected, boolean vulnerable) { }
    public record FocusedGraphDto(UUID occurrenceId, UUID targetProjectId, UUID rootTreeNodeId,
                                  UUID targetTreeNodeId, boolean targetReachable, Instant generatedAt,
                                  String sourceCommitSha, List<GraphNode> nodes) { }
    public record TargetProjectDto(UUID projectId, String name, String groupId, String artifactId,
                                   String version, String pomPath, boolean current, boolean recommended,
                                   boolean recommendationAvailable) { }
    public record ChangeSelectionRequest(UUID occurrenceId, UUID targetProjectId,
                                         UUID changePointTreeNodeId, UUID targetArtifactVersionId,
                                         UpdateStrategy strategy) { }
    public record ChangeSelectionDto(UUID findingId, UUID occurrenceId, UUID targetProjectId,
                                     UUID changePointTreeNodeId, UUID targetArtifactVersionId,
                                     UpdateStrategy strategy, Instant selectedAt) { }
}
