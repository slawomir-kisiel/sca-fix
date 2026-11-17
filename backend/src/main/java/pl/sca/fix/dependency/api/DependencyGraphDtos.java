package pl.sca.fix.dependency.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DependencyGraphDtos {
    private DependencyGraphDtos() { }
    public record ArtifactVersionDto(UUID treeNodeId, UUID artifactVersionId, String groupId, String artifactId, String version,
                                     boolean locallyManaged, UUID projectId, Instant dependencyTreeAnalyzedAt,
                                     String analysisStatus, boolean hasChildren) { }
    public record DependencyEdgeDto(UUID sourceTreeNodeId, UUID targetTreeNodeId, String scope,
                                    boolean direct, boolean transitive, boolean optional, boolean managed, String relation) { }
    public record DependencyGraphDto(UUID projectId, Instant analyzedAt, String analyzedCommitSha,
                                     String analysisStatus, List<ArtifactVersionDto> nodes,
                                     List<DependencyEdgeDto> edges, List<UUID> unanalysedProjects,
                                     UUID jobId) { }
    public record DependencyAnalysisResponse(UUID jobId, String status) { }
    public record BatchDependencyAnalysisResponse(List<DependencyAnalysisResponse> jobs, List<UUID> skippedProjectIds) { }
}
