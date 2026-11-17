package pl.sca.fix.report.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.sca.fix.artifact.domain.Artifact;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.dependency.domain.ArtifactTreeNode;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.report.api.ChangePointGraphDtos.FocusedGraphDto;
import pl.sca.fix.report.api.ChangePointGraphDtos.GraphNode;
import pl.sca.fix.report.api.ChangePointGraphDtos.TargetProjectDto;
import pl.sca.fix.report.domain.DependencyTreeOccurrence;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;

@Service
public class ChangePointGraphService {
    private final DependencyTreeOccurrenceRepository occurrences;
    private final ArtifactTreeNodeRepository nodes;
    private final ArtifactVersionRepository versions;
    private final ArtifactRepository artifacts;
    private final ProjectRepository projects;

    public ChangePointGraphService(DependencyTreeOccurrenceRepository occurrences, ArtifactTreeNodeRepository nodes,
                                   ArtifactVersionRepository versions, ArtifactRepository artifacts, ProjectRepository projects) {
        this.occurrences = occurrences; this.nodes = nodes; this.versions = versions; this.artifacts = artifacts; this.projects = projects;
    }

    @Transactional(readOnly = true)
    public FocusedGraphDto get(UUID findingId, UUID occurrenceId, UUID targetProjectId) {
        DependencyTreeOccurrence occurrence = occurrences.findById(occurrenceId).orElseThrow(() -> notFound("Occurrence not found"));
        if (!findingId.equals(occurrence.getFindingId())) throw badRequest("Occurrence does not belong to finding");
        Project targetProject = projects.findById(targetProjectId).orElseThrow(() -> notFound("Project not found"));
        List<ArtifactTreeNode> path = path(occurrence.getVulnerableArtifactTreeNodeId());
        int start = 0;
        boolean foundTarget = false;
        for (int index = 0; index < path.size(); index++) {
            ArtifactTreeNode node = path.get(index);
            Artifact artifact = versions.findById(node.getArtifactVersionId()).flatMap(version -> artifacts.findById(version.getArtifactId())).orElse(null);
            if (artifact != null && targetProjectId.equals(artifact.getProjectId())) {
                start = index;
                foundTarget = true;
            }
        }
        if (!foundTarget) throw badRequest("Target project is not part of the occurrence path");
        List<ArtifactTreeNode> selectedPath = path.subList(Math.min(start, path.size()), path.size());
        List<GraphNode> graphNodes = selectedPath.stream().map(node -> nodeDto(node, occurrence.getVulnerableArtifactTreeNodeId().equals(node.getId()))).toList();
        UUID root = selectedPath.isEmpty() ? null : selectedPath.get(0).getId();
        return new FocusedGraphDto(occurrenceId, targetProjectId, root, occurrence.getVulnerableArtifactTreeNodeId(),
                !selectedPath.isEmpty(), Instant.now(), targetProject.getBaseCommitSha(), graphNodes);
    }

    @Transactional(readOnly = true)
    public List<TargetProjectDto> targetProjects(UUID findingId, UUID occurrenceId) {
        DependencyTreeOccurrence occurrence = occurrences.findById(occurrenceId)
                .orElseThrow(() -> notFound("Occurrence not found"));
        if (!findingId.equals(occurrence.getFindingId())) throw badRequest("Occurrence does not belong to finding");
        List<ArtifactTreeNode> path = path(occurrence.getVulnerableArtifactTreeNodeId());
        java.util.LinkedHashSet<UUID> projectIds = new java.util.LinkedHashSet<>();
        for (ArtifactTreeNode node : path) {
            versions.findById(node.getArtifactVersionId())
                    .flatMap(version -> artifacts.findById(version.getArtifactId()))
                    .map(Artifact::getProjectId)
                    .ifPresent(projectId -> projectIds.add(projectId));
        }
        List<Project> candidates = projectIds.stream().map(projects::findById).flatMap(java.util.Optional::stream).toList();
        ProjectRecommendation recommendation = recommendProject(path, candidates);
        return candidates.stream().map(project -> new TargetProjectDto(project.getId(), project.getName(), project.getGroupId(),
                project.getArtifactId(), project.getVersion(), project.getPomPath(), project.getId().equals(occurrence.getProjectId()),
                project.getId().equals(recommendation.projectId()), recommendation.available())).toList();
    }

    private ProjectRecommendation recommendProject(List<ArtifactTreeNode> path, List<Project> candidates) {
        for (int index = path.size() - 1; index >= 0; index--) {
            UUID projectId = projectId(path.get(index));
            if (projectId != null && candidates.stream().anyMatch(project -> projectId.equals(project.getId())))
                return new ProjectRecommendation(projectId, true);
        }
        return new ProjectRecommendation(candidates.stream().findFirst().map(Project::getId).orElse(null), true);
    }

    private UUID projectId(ArtifactTreeNode node) {
        return versions.findById(node.getArtifactVersionId()).flatMap(version -> artifacts.findById(version.getArtifactId()))
                .map(Artifact::getProjectId).orElse(null);
    }

    private record ProjectRecommendation(UUID projectId, boolean available) { }

    private List<ArtifactTreeNode> path(UUID endId) {
        List<ArtifactTreeNode> result = new ArrayList<>();
        UUID current = endId;
        while (current != null) {
            ArtifactTreeNode node = nodes.findById(current).orElseThrow(() -> notFound("Tree node not found"));
            result.add(node); current = node.getPreviousNodeId();
        }
        Collections.reverse(result); return result;
    }

    private GraphNode nodeDto(ArtifactTreeNode node, boolean vulnerable) {
        var version = versions.findById(node.getArtifactVersionId()).orElse(null);
        Artifact artifact = version == null ? null : artifacts.findById(version.getArtifactId()).orElse(null);
        return new GraphNode(node.getId(), node.getArtifactVersionId(), artifact == null ? null : artifact.getGroupId(),
                artifact == null ? null : artifact.getArtifactId(), version == null ? null : version.getVersion(),
                artifact != null && artifact.getProjectId() != null, artifact == null ? null : artifact.getProjectId(),
                version == null ? "NOT_STARTED" : version.getAnalysisStatus().name(), false, vulnerable);
    }

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
