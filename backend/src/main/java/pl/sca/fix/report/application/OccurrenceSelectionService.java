package pl.sca.fix.report.application;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.report.api.OccurrenceSelectionDtos.OccurrenceOptionDto;
import pl.sca.fix.report.api.OccurrenceSelectionDtos.OccurrencePathNodeDto;
import pl.sca.fix.report.domain.DependencyTreeOccurrence;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;

@Service
public class OccurrenceSelectionService {
    private final VulnerabilityFindingRepository findings;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final ProjectRepository projects;
    private final ArtifactVersionRepository versions;
    private final ArtifactRepository artifacts;
    private final ArtifactTreeNodeRepository nodes;

    public OccurrenceSelectionService(VulnerabilityFindingRepository findings,
                                      DependencyTreeOccurrenceRepository occurrences,
                                      ProjectRepository projects,
                                      ArtifactVersionRepository versions,
                                      ArtifactRepository artifacts, ArtifactTreeNodeRepository nodes) {
        this.findings = findings;
        this.occurrences = occurrences;
        this.projects = projects;
        this.versions = versions;
        this.artifacts = artifacts; this.nodes = nodes;
    }

    @Transactional(readOnly = true)
    public List<OccurrenceOptionDto> list(UUID findingId) {
        var finding = finding(findingId);
        return occurrences.findByFindingId(finding.getId()).stream()
                .map(item -> option(item, null))
                .toList();
    }

    private pl.sca.fix.report.domain.VulnerabilityFinding finding(UUID id) {
        return findings.findById(id).orElseThrow(() -> notFound("Finding not found"));
    }

    private OccurrenceOptionDto option(DependencyTreeOccurrence item, UUID selectedId) {
        var project = projects.findById(item.getProjectId()).orElse(null);
        var treeNode = nodes.findById(item.getVulnerableArtifactTreeNodeId()).orElse(null);
        var version = treeNode == null ? null : versions.findById(treeNode.getArtifactVersionId()).orElse(null);
        var artifact = version == null ? null : artifacts.findById(version.getArtifactId()).orElse(null);
        return new OccurrenceOptionDto(item.getId(), item.getProjectId(), treeNode == null ? null : treeNode.getArtifactVersionId(),
                treeNode == null ? null : treeNode.getId(),
                project == null ? null : project.getName(), project == null ? null : project.getPomPath(),
                artifact == null ? null : artifact.getGroupId(), artifact == null ? null : artifact.getArtifactId(),
                item.getResolvedVersion(), item.getDefinitionSource(), item.isSourcesAvailable(), item.getId().equals(selectedId),
                treeNode == null ? List.of() : path(treeNode.getId()));
    }

    private List<OccurrencePathNodeDto> path(UUID endId) {
        List<OccurrencePathNodeDto> result = new ArrayList<>();
        UUID current = endId;
        while (current != null) {
            var node = nodes.findById(current).orElse(null);
            if (node == null) break;
            var version = versions.findById(node.getArtifactVersionId()).orElse(null);
            var artifact = version == null ? null : artifacts.findById(version.getArtifactId()).orElse(null);
            result.add(new OccurrencePathNodeDto(node.getId(), node.getArtifactVersionId(),
                    artifact == null ? null : artifact.getGroupId(), artifact == null ? null : artifact.getArtifactId(),
                    version == null ? null : version.getVersion(), node.getRelationType().name(),
                    artifact != null && artifact.getProjectId() != null, node.getId().equals(endId)));
            current = node.getPreviousNodeId();
        }
        Collections.reverse(result);
        return result;
    }

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
