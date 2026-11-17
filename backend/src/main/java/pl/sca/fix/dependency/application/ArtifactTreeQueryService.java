package pl.sca.fix.dependency.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import pl.sca.fix.artifact.domain.Artifact;
import pl.sca.fix.artifact.domain.ArtifactVersion;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.dependency.api.DependencyGraphDtos.ArtifactVersionDto;
import pl.sca.fix.dependency.api.DependencyGraphDtos.DependencyEdgeDto;
import pl.sca.fix.dependency.api.DependencyGraphDtos.DependencyGraphDto;
import pl.sca.fix.dependency.domain.ArtifactTreeNode;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;

@Service
public class ArtifactTreeQueryService {
    private final ProjectRepository projects;
    private final ArtifactTreeNodeRepository nodes;
    private final ArtifactVersionRepository versions;
    private final ArtifactRepository artifacts;

    public ArtifactTreeQueryService(ProjectRepository projects, ArtifactTreeNodeRepository nodes,
                                    ArtifactVersionRepository versions, ArtifactRepository artifacts) {
        this.projects = projects; this.nodes = nodes; this.versions = versions; this.artifacts = artifacts;
    }

    public DependencyGraphDto get(UUID projectId) {
        Project project = project(projectId);
        return graph(project, nodes.findByProjectIdAndPreviousNodeIdIsNull(projectId));
    }

    public DependencyGraphDto getChildren(UUID projectId, UUID treeNodeId) {
        Project project = project(projectId);
        ArtifactTreeNode source = nodes.findById(treeNodeId).orElseThrow(() -> new IllegalArgumentException("Tree node not found: " + treeNodeId));
        if (!projectId.equals(source.getProjectId())) throw new IllegalArgumentException("Tree node does not belong to project");
        return graph(project, List.of(source), nodes.findByPreviousNodeId(treeNodeId));
    }

    private DependencyGraphDto graph(Project project, List<ArtifactTreeNode> sources) {
        List<ArtifactTreeNode> children = new ArrayList<>();
        for (ArtifactTreeNode source : sources) children.addAll(nodes.findByPreviousNodeId(source.getId()));
        return graph(project, sources, children);
    }

    private DependencyGraphDto graph(Project project, List<ArtifactTreeNode> sources, List<ArtifactTreeNode> children) {
        List<ArtifactTreeNode> allNodes = new ArrayList<>(sources);
        allNodes.addAll(children);
        Map<UUID, ArtifactVersion> versionsById = versions.findAllById(allNodes.stream().map(ArtifactTreeNode::getArtifactVersionId).distinct().toList())
                .stream().collect(Collectors.toMap(ArtifactVersion::getId, Function.identity()));
        Map<UUID, Artifact> artifactsById = artifacts.findAllById(versionsById.values().stream().map(ArtifactVersion::getArtifactId).distinct().toList())
                .stream().collect(Collectors.toMap(Artifact::getId, Function.identity()));
        List<ArtifactVersionDto> nodeDtos = allNodes.stream().map(node -> {
            ArtifactVersion version = versionsById.get(node.getArtifactVersionId());
            Artifact artifact = version == null ? null : artifactsById.get(version.getArtifactId());
            boolean hasChildren = !nodes.findByPreviousNodeId(node.getId()).isEmpty();
            return new ArtifactVersionDto(node.getId(), node.getArtifactVersionId(), artifact == null ? "" : artifact.getGroupId(),
                    artifact == null ? "" : artifact.getArtifactId(), version == null ? "" : version.getVersion(),
                    artifact != null && artifact.getProjectId() != null, artifact == null ? null : artifact.getProjectId(),
                    version == null ? null : version.getAnalyzedAt(), version == null ? "NOT_STARTED" : version.getAnalysisStatus().name(), hasChildren);
        }).toList();
        List<DependencyEdgeDto> edgeDtos = children.stream().map(node -> new DependencyEdgeDto(node.getPreviousNodeId(), node.getId(),
                node.getScope(), node.isDirect(), node.getDepth() > 0, false, node.isManaged(), node.getRelationType().name())).toList();
        Instant analyzedAt = versionsById.values().stream().map(ArtifactVersion::getAnalyzedAt).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
        String status = nodeDtos.stream().anyMatch(node -> "FAILED".equals(node.analysisStatus())) ? "FAILED" :
                nodeDtos.stream().anyMatch(node -> "PARTIAL".equals(node.analysisStatus())) ? "PARTIAL" :
                nodeDtos.stream().anyMatch(node -> "READY".equals(node.analysisStatus())) ? "READY" : "NOT_STARTED";
        return new DependencyGraphDto(project.getId(), analyzedAt, project.getBaseCommitSha(), status, nodeDtos, edgeDtos, List.of(), project.getMavenAnalysisJobId());
    }

    private Project project(UUID projectId) { return projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId)); }
}
