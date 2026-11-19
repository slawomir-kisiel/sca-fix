package pl.sca.fix.report.application;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.report.api.UpdatePlanDtos.CreatePlanRequest;
import pl.sca.fix.report.api.UpdatePlanDtos.PlanDetailsDto;
import pl.sca.fix.report.api.UpdatePlanDtos.PlanItemDto;
import pl.sca.fix.report.api.UpdatePlanDtos.PlanSummaryDto;
import pl.sca.fix.report.domain.PlanItem;
import pl.sca.fix.report.domain.PlanItemStatus;
import pl.sca.fix.report.domain.UpdatePlan;
import pl.sca.fix.report.domain.UpdatePlanStatus;
import pl.sca.fix.report.persistence.PlanItemRepository;
import pl.sca.fix.report.persistence.UpdatePlanRepository;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.repository.persistence.RepositoryRepository;

@Service
public class UpdatePlanService {
    private final UpdatePlanRepository plans;
    private final PlanItemRepository items;
    private final ArtifactTreeNodeRepository nodes;
    private final ArtifactVersionRepository versions;
    private final ArtifactRepository artifacts;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final ProjectRepository projects;
    private final RepositoryRepository repositories;

    public UpdatePlanService(UpdatePlanRepository plans, PlanItemRepository items,
                             ArtifactTreeNodeRepository nodes, ArtifactVersionRepository versions,
                             ArtifactRepository artifacts, DependencyTreeOccurrenceRepository occurrences,
                             ProjectRepository projects, RepositoryRepository repositories) {
        this.plans = plans; this.items = items; this.nodes = nodes; this.versions = versions;
        this.artifacts = artifacts; this.occurrences = occurrences; this.projects = projects;
        this.repositories = repositories;
    }

    @Transactional(readOnly = true)
    public List<PlanSummaryDto> list() {
        return plans.findAllByOrderByCreatedAtDesc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanSummaryDto> list(UUID repositoryId) {
        return plans.findByRepositoryIdOrderByCreatedAtDesc(repositoryId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public PlanDetailsDto details(UUID id) {
        UpdatePlan plan = plan(id);
        List<PlanItemDto> planItems = items.findByUpdatePlanIdOrderBySelectedAtAsc(id).stream()
                .map(this::item).toList();
        return new PlanDetailsDto(summary(plan), planItems, warnings(planItems));
    }

    @Transactional(readOnly = true)
    public PlanSummaryDto active() {
        return plans.findByActiveTrue().map(this::summary).orElse(null);
    }

    @Transactional(readOnly = true)
    public PlanSummaryDto active(UUID repositoryId) {
        return plans.findByRepositoryIdAndActiveTrue(repositoryId).map(this::summary).orElse(null);
    }

    @Transactional
    public PlanSummaryDto create(CreatePlanRequest request) {
        if (request == null || request.repositoryId() == null || !repositories.existsById(request.repositoryId()))
            throw badRequest("repositoryId is required");
        deactivateCurrent(request.repositoryId());
        String name = request.name() == null || request.name().isBlank()
                ? "Plan aktualizacji" : request.name().trim();
        String taskId = "SCA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return summary(plans.save(new UpdatePlan(UUID.randomUUID(), request.repositoryId(), taskId, name)));
    }

    @Transactional
    public PlanSummaryDto activate(UUID id) {
        UpdatePlan selected = plan(id);
        if (selected.getStatus() != UpdatePlanStatus.PLANNED) throw badRequest("Only PLANNED plans can be active");
        deactivateCurrent(selected.getRepositoryId()); selected.activate();
        return summary(plans.save(selected));
    }

    @Transactional
    public PlanSummaryDto changeStatus(UUID id, UpdatePlanStatus status) {
        if (status == null) throw badRequest("status is required");
        UpdatePlan plan = plan(id);
        if (status == UpdatePlanStatus.PLANNED) { deactivateCurrent(plan.getRepositoryId()); plan.activate(); }
        plan.changeStatus(status);
        return summary(plans.save(plan));
    }

    @Transactional
    public void delete(UUID id) {
        UpdatePlan plan = plan(id);
        if (plan.getStatus() == UpdatePlanStatus.APPLIED || plan.getStatus() == UpdatePlanStatus.COMMITTED)
            throw conflict("Applied or committed plans cannot be deleted");
        items.deleteByUpdatePlanId(id);
        plans.delete(plan);
    }

    @Transactional
    public void deleteItem(UUID planId, UUID itemId) {
        UpdatePlan plan = plan(planId);
        if (plan.getStatus() != UpdatePlanStatus.PLANNED && plan.getStatus() != UpdatePlanStatus.FAILED)
            throw conflict("PlanItem can be deleted only from PLANNED or FAILED plan");
        PlanItem item = items.findById(itemId).orElseThrow(() -> notFound("PlanItem not found"));
        if (!planId.equals(item.getUpdatePlanId())) throw notFound("PlanItem not found");
        if (item.getStatus() == PlanItemStatus.APPLIED) throw conflict("Applied PlanItem cannot be deleted");
        items.delete(item);
    }

    private void deactivateCurrent(UUID repositoryId) { plans.findByRepositoryIdAndActiveTrue(repositoryId).ifPresent(plan -> { plan.deactivate(); plans.save(plan); }); }
    private UpdatePlan plan(UUID id) { return plans.findById(id).orElseThrow(() -> notFound("Update plan not found")); }
    private PlanSummaryDto summary(UpdatePlan plan) { return new PlanSummaryDto(plan.getId(), plan.getRepositoryId(), plan.getTaskId(), plan.getName(), plan.getStatus(), plan.isActive(), plan.getCreatedAt(), plan.getUpdatedAt(), items.countByUpdatePlanId(plan.getId()), plan.getBranchName(), plan.getBaseCommitSha()); }
    private PlanItemDto item(PlanItem item) {
        var currentVersion = nodes.findById(item.getChangePointTreeNodeId())
                .flatMap(node -> versions.findById(node.getArtifactVersionId())).orElse(null);
        var artifact = currentVersion == null
                ? versions.findById(item.getTargetArtifactVersionId()).flatMap(version -> artifacts.findById(version.getArtifactId())).orElse(null)
                : artifacts.findById(currentVersion.getArtifactId()).orElse(null);
        String current = currentVersion == null ? null : currentVersion.getVersion();
        String target = versions.findById(item.getTargetArtifactVersionId()).map(v -> v.getVersion()).orElse(null);
        String projectName = occurrences.findById(item.getOccurrenceId())
                .flatMap(occurrence -> projects.findById(occurrence.getProjectId()))
                .map(project -> project.getName()).orElse(null);
        return new PlanItemDto(item.getId(), item.getUpdatePlanId(), item.getFindingId(), item.getOccurrenceId(), projectName, item.getTargetProjectId(), item.getChangePointTreeNodeId(), item.getTargetArtifactVersionId(),
                artifact == null ? null : artifact.getGroupId(), artifact == null ? null : artifact.getArtifactId(), current, target,
                item.getStrategy(), item.getStatus(), item.getAppliedFromVersion(), item.getErrorMessage(), item.getSelectedAt());
    }
    private List<String> warnings(List<PlanItemDto> items) { return items.stream().filter(item -> item.currentVersion() == null || item.targetVersion() == null).map(item -> "Nie można odtworzyć wersji dla PlanItem " + item.itemId()).toList(); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
