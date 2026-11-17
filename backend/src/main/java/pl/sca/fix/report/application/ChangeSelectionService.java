package pl.sca.fix.report.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.report.api.ChangePointGraphDtos.ChangeSelectionDto;
import pl.sca.fix.report.api.ChangePointGraphDtos.ChangeSelectionRequest;
import pl.sca.fix.report.domain.PlanItem;
import pl.sca.fix.report.domain.UpdatePlan;
import pl.sca.fix.report.domain.UpdatePlanStatus;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.report.persistence.PlanItemRepository;
import pl.sca.fix.report.persistence.UpdatePlanRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;

@Service
public class ChangeSelectionService {
    private final VulnerabilityFindingRepository findings;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final ProjectRepository projects;
    private final ArtifactVersionRepository versions;
    private final ArtifactTreeNodeRepository nodes;
    private final PlanItemRepository selections;
    private final UpdatePlanRepository plans;

    public ChangeSelectionService(VulnerabilityFindingRepository findings, DependencyTreeOccurrenceRepository occurrences,
                                  ProjectRepository projects, ArtifactVersionRepository versions,
                                  ArtifactTreeNodeRepository nodes, PlanItemRepository selections,
                                  UpdatePlanRepository plans) {
        this.findings = findings; this.occurrences = occurrences; this.projects = projects;
        this.versions = versions; this.nodes = nodes; this.selections = selections; this.plans = plans;
    }

    @Transactional
    public ChangeSelectionDto save(UUID findingId, ChangeSelectionRequest request) {
        if (request == null || request.occurrenceId() == null || request.targetProjectId() == null
                || request.changePointTreeNodeId() == null || request.targetArtifactVersionId() == null
                || request.strategy() == null) throw badRequest("All UI-12 selections are required");
        var targetProject = projects.findById(request.targetProjectId()).orElseThrow(() -> notFound("Target project not found"));
        UpdatePlan activePlan = plans.findByRepositoryIdAndActiveTrue(targetProject.getRepositoryId())
                .filter(plan -> plan.getStatus() == UpdatePlanStatus.PLANNED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active PLANNED update plan"));
        if (!findings.existsById(findingId)) throw notFound("Finding not found");
        var occurrence = occurrences.findById(request.occurrenceId()).orElseThrow(() -> notFound("Occurrence not found"));
        if (!findingId.equals(occurrence.getFindingId())) throw badRequest("Occurrence does not belong to finding");
        var node = nodes.findById(request.changePointTreeNodeId()).orElseThrow(() -> notFound("Change point tree node not found"));
        if (!occurrence.getProjectId().equals(node.getProjectId())) throw badRequest("Change point does not belong to occurrence tree");
        var changePointVersion = versions.findById(node.getArtifactVersionId())
                .orElseThrow(() -> notFound("Change point artifact version not found"));
        var targetVersion = versions.findById(request.targetArtifactVersionId())
                .orElseThrow(() -> notFound("Target artifact version not found"));
        if (!changePointVersion.getArtifactId().equals(targetVersion.getArtifactId()))
            throw badRequest("Target version must belong to the selected change point artifact");
        var selection = selections.findByUpdatePlanIdAndOccurrenceId(activePlan.getId(), request.occurrenceId()).orElseGet(() ->
                new PlanItem(activePlan.getId(), findingId, request.occurrenceId(), request.targetProjectId(),
                        request.changePointTreeNodeId(), request.strategy(), request.targetArtifactVersionId()));
        if (selection.getId() != null) selection.change(request.targetProjectId(), request.changePointTreeNodeId(),
                request.strategy(), request.targetArtifactVersionId());
        selection = selections.save(selection);
        return new ChangeSelectionDto(findingId, request.occurrenceId(), request.targetProjectId(),
                request.changePointTreeNodeId(), request.targetArtifactVersionId(), request.strategy(), selection.getSelectedAt());
    }

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
