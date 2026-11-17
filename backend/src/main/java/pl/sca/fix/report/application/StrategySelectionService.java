package pl.sca.fix.report.application;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.sca.fix.artifact.domain.Artifact;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.report.api.StrategyDtos.StrategyImpactDto;
import pl.sca.fix.report.api.StrategyDtos.StrategyOptionDto;
import pl.sca.fix.report.api.StrategyDtos.StrategyOptionsDto;
import pl.sca.fix.report.api.StrategyDtos.VersionDefinitionDto;
import pl.sca.fix.report.domain.DependencyTreeOccurrence;
import pl.sca.fix.report.domain.PlanItem;
import pl.sca.fix.report.domain.UpdateStrategy;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.report.persistence.PlanItemRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;

@Service
public class StrategySelectionService {
    private final VulnerabilityFindingRepository findings;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final PlanItemRepository selections;
    private final ArtifactRepository artifacts;
    private final ArtifactVersionRepository versions;
    private final ArtifactTreeNodeRepository dependencies;
    private final ProjectRepository projects;
    private final MavenVersionDefinitionLocator definitionLocator;

    public StrategySelectionService(VulnerabilityFindingRepository findings,
                                    DependencyTreeOccurrenceRepository occurrences,
                                    PlanItemRepository selections,
                                    ArtifactRepository artifacts, ArtifactVersionRepository versions,
                                    ArtifactTreeNodeRepository dependencies, ProjectRepository projects,
                                    MavenVersionDefinitionLocator definitionLocator) {
        this.findings = findings; this.occurrences = occurrences; this.selections = selections;
        this.artifacts = artifacts; this.versions = versions; this.dependencies = dependencies;
        this.projects = projects; this.definitionLocator = definitionLocator;
    }

    @Transactional(readOnly = true)
    public StrategyOptionsDto list(UUID findingId, UUID occurrenceId, UUID targetArtifactVersionId) {
        var occurrence = occurrence(findingId, occurrenceId);
        var node = dependencies.findById(occurrence.getVulnerableArtifactTreeNodeId())
                .orElseThrow(() -> notFound("Occurrence tree node not found"));
        return list(findingId, occurrenceId, occurrence.getProjectId(), node.getArtifactVersionId(), targetArtifactVersionId,
                node.isDirect());
    }

    @Transactional(readOnly = true)
    public StrategyOptionsDto list(UUID findingId, UUID occurrenceId, UUID targetProjectId,
                                   UUID changePointArtifactVersionId, UUID targetArtifactVersionId) {
        return list(findingId, occurrenceId, targetProjectId, changePointArtifactVersionId, targetArtifactVersionId,
                hasDirectPath(targetProjectId, changePointArtifactVersionId));
    }

    private StrategyOptionsDto list(UUID findingId, UUID occurrenceId, UUID targetProjectId,
                                    UUID changePointArtifactVersionId, UUID targetArtifactVersionId,
                                    boolean direct) {
        var finding = finding(findingId);
        var occurrence = occurrence(findingId, occurrenceId);
        var changePoint = versions.findById(changePointArtifactVersionId)
                .orElseThrow(() -> notFound("Change point artifact version not found"));
        var changePointArtifact = artifacts.findById(changePoint.getArtifactId())
                .orElseThrow(() -> notFound("Change point artifact not found"));
        Project project = projects.findById(targetProjectId)
                .orElseThrow(() -> notFound("Project not found"));
        VersionDefinitionDto definition = definitionLocator.locate(project,
                changePointArtifact.getGroupId(), changePointArtifact.getArtifactId());
        boolean localProperty = definition.propertyName() != null && definition.sourcePomPath() != null
                && (project.getGroupId() + ":" + project.getArtifactId()).equals(definition.sourceProject());
        PomCapabilities pom = new PomCapabilities(
                localProperty,
                "dependencyManagement".equals(definition.section()));
        boolean directDefinition = direct && !localProperty;
        List<StrategyOptionDto> options = List.of(
                option(UpdateStrategy.PROPERTY, "Zmiana property", pom.property(), "VERSION_PROPERTY_NOT_FOUND"),
                option(UpdateStrategy.DIRECT_DEPENDENCY, "Aktualizacja bezpośredniej zależności", directDefinition,
                        "OCCURRENCE_NOT_REACHABLE_FROM_DIRECT_DEPENDENCY"),
                option(UpdateStrategy.DEPENDENCY_MANAGEMENT, "Zmiana w dependencyManagement", pom.dependencyManagement(),
                        "DEPENDENCY_MANAGEMENT_ENTRY_NOT_FOUND"),
                option(UpdateStrategy.CVE_OVERRIDE, "CVE Override (dependencyManagement)", true, null),
                option(UpdateStrategy.DEFER, "Odłóż", true, null),
                option(UpdateStrategy.REJECT, "Odrzuć", true, null));
        long total = occurrences.countByFindingId(findingId);
        var selected = selections.findFirstByOccurrenceIdOrderBySelectedAtDesc(occurrenceId).map(PlanItem::getStrategy).orElse(null);
        UpdateStrategy recommended = localProperty ? UpdateStrategy.PROPERTY
                : directDefinition ? UpdateStrategy.DIRECT_DEPENDENCY
                : pom.dependencyManagement() ? UpdateStrategy.DEPENDENCY_MANAGEMENT : UpdateStrategy.CVE_OVERRIDE;
        String warning = total > 1 ? "Wybór może wpłynąć na " + (total - 1) + " innych wystąpień tego findingu." : null;
        return new StrategyOptionsDto(findingId, occurrenceId, targetArtifactVersionId, options,
                new StrategyImpactDto(total, Math.max(0, total - 1), warning), selected, recommended, definition);
    }

    @Transactional(readOnly = true)
    public StrategyOptionsDto listForTreeNode(UUID findingId, UUID occurrenceId, UUID targetProjectId,
                                              UUID changePointTreeNodeId, UUID targetArtifactVersionId) {
        var node = dependencies.findById(changePointTreeNodeId)
                .orElseThrow(() -> notFound("Change point tree node not found"));
        var occurrence = occurrence(findingId, occurrenceId);
        if (!isOnOccurrencePath(occurrence.getVulnerableArtifactTreeNodeId(), changePointTreeNodeId))
            throw badRequest("Change point does not belong to occurrence tree");
        return list(findingId, occurrenceId, targetProjectId, node.getArtifactVersionId(), targetArtifactVersionId,
                node.isDirect());
    }

    private boolean isOnOccurrencePath(UUID vulnerableNodeId, UUID candidateNodeId) {
        UUID current = vulnerableNodeId;
        while (current != null) {
            if (current.equals(candidateNodeId)) return true;
            current = dependencies.findById(current).map(node -> node.getPreviousNodeId()).orElse(null);
        }
        return false;
    }

    private boolean hasDirectPath(UUID projectId, UUID targetVersionId) {
        var project = projects.findById(projectId).orElse(null);
        if (project == null) return false;
        var root = artifacts.findByProjectId(project.getId())
                .flatMap(artifact -> versions.findByArtifactIdAndVersion(artifact.getId(), project.getVersion()));
        if (root.isEmpty()) return false;
        ArrayDeque<UUID> queue = new ArrayDeque<>(); queue.add(root.get().getId());
        Set<UUID> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            UUID source = queue.remove(); if (!visited.add(source)) continue;
            for (var edge : dependencies.findByPreviousNodeId(source)) {
                if (edge.getArtifactVersionId().equals(targetVersionId)) return edge.isDirect();
                queue.add(edge.getId());
            }
        }
        return false;
    }

    private StrategyOptionDto option(UpdateStrategy strategy, String label, boolean eligible, String reason) {
        return new StrategyOptionDto(strategy, label, eligible, eligible ? null : reason);
    }
    private pl.sca.fix.report.domain.VulnerabilityFinding finding(UUID id) { return findings.findById(id).orElseThrow(() -> notFound("Finding not found")); }
    private UUID nodeVersionId(DependencyTreeOccurrence occurrence) {
        return dependencies.findById(occurrence.getVulnerableArtifactTreeNodeId()).map(item -> item.getArtifactVersionId())
                .orElseThrow(() -> notFound("Occurrence tree node not found"));
    }
    private DependencyTreeOccurrence occurrence(UUID findingId, UUID id) {
        var item = occurrences.findById(id).orElseThrow(() -> notFound("Occurrence not found"));
        if (!findingId.equals(item.getFindingId())) throw badRequest("Occurrence does not belong to finding");
        return item;
    }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record PomCapabilities(boolean property, boolean dependencyManagement) { }
}
