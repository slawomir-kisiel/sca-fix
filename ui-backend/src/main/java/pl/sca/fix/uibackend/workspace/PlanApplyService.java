package pl.sca.fix.uibackend.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.plan.PlanApplyRequest;
import pl.sca.fix.core.plan.PlanApplyResult;
import pl.sca.fix.core.plan.PlanDocument;
import pl.sca.fix.core.plan.PlanDocumentJsonCodec;
import pl.sca.fix.core.plan.PlanItem;
import pl.sca.fix.core.port.GitPort;

@Service
public class PlanApplyService {
    private final WorkspaceRepository workspaces;
    private final GitPort git;
    private final PlanDocumentJsonCodec codec = new PlanDocumentJsonCodec();
    private final Map<UUID, PlanApplyJob> jobs = new ConcurrentHashMap<>();

    public PlanApplyService(WorkspaceRepository workspaces, GitPort git) { this.workspaces = workspaces; this.git = git; }

    public PlanApplyJob start(UUID workspaceId, String planId, List<String> itemIds, boolean retry) {
        WorkspaceEntity workspace = workspace(workspaceId);
        PlanDocument plan = read(workspace);
        if (!plan.planId().equals(planId)) throw new WorkspaceException("PLAN_NOT_FOUND", "Plan not found: " + planId);
        List<String> selected = itemIds == null || itemIds.isEmpty()
                ? plan.items().stream().filter(item -> !"APPLIED".equalsIgnoreCase(item.status()) && !"SKIPPED".equalsIgnoreCase(item.status())).map(PlanItem::itemId).toList()
                : itemIds.stream().distinct().toList();
        if (selected.isEmpty()) throw new WorkspaceException("NO_PLAN_ITEMS_SELECTED", "No applicable PlanItem selected");
        List<PlanItem> items = plan.items().stream().filter(item -> selected.contains(item.itemId())).toList();
        PlanDocument selectedPlan = new PlanDocument(plan.schemaVersion(), plan.planId(), plan.createdAt(), plan.repository(), plan.updateLevel(), items, plan.warnings(), plan.errors(), plan.status());
        UUID jobId = UUID.randomUUID();
        jobs.put(jobId, new PlanApplyJob(jobId, workspaceId, planId, "QUEUED", selected.size(), 0, 0, 0, null, Instant.now(), null));
        CompletableFuture.runAsync(() -> execute(jobId, workspace, plan, selectedPlan, retry));
        return jobs.get(jobId);
    }

    public PlanApplyJob get(UUID workspaceId, UUID jobId) {
        PlanApplyJob job = jobs.get(jobId);
        if (job == null || !job.workspaceId().equals(workspaceId)) throw new WorkspaceException("JOB_NOT_FOUND", "Apply job not found: " + jobId);
        return job;
    }

    private void execute(UUID jobId, WorkspaceEntity workspace, PlanDocument original, PlanDocument selected, boolean retry) {
        jobs.computeIfPresent(jobId, (id, job) -> job.running());
        try {
            PlanApplyResult result = new pl.sca.fix.core.plan.PlanApplyService(new NioFileSystemAdapter(), git)
                    .apply(new PlanApplyRequest(selected, Path.of(workspace.getRepositoryPath()), false, retry, false));
            List<PlanItem> updated = original.items().stream().map(item -> selected.items().stream().filter(selectedItem -> selectedItem.itemId().equals(item.itemId())).findFirst().map(selectedItem -> result.plan().items().stream().filter(resultItem -> resultItem.itemId().equals(item.itemId())).findFirst().orElse(item)).orElse(item)).toList();
            save(workspace, new PlanDocument(original.schemaVersion(), original.planId(), original.createdAt(), original.repository(), original.updateLevel(), updated, result.plan().warnings(), result.plan().errors(), result.plan().status()));
            jobs.computeIfPresent(jobId, (id, job) -> job.completed(result.summary().applied(), result.summary().failed(), result.summary().skipped(), null, Instant.now()));
        } catch (Exception exception) {
            jobs.computeIfPresent(jobId, (id, job) -> job.failed(exception.getMessage(), Instant.now()));
        }
    }

    private PlanDocument read(WorkspaceEntity workspace) { try { return codec.read(Files.readAllBytes(planPath(workspace))); } catch (Exception exception) { throw new WorkspaceException("PLAN_READ_FAILED", exception.getMessage()); } }
    private void save(WorkspaceEntity workspace, PlanDocument plan) { try { Files.write(planPath(workspace), codec.write(plan)); } catch (Exception exception) { throw new WorkspaceException("PLAN_WRITE_FAILED", exception.getMessage()); } }
    private static Path planPath(WorkspaceEntity workspace) { return Path.of(workspace.getRepositoryPath()).resolve("target/scafix/reports/plan.json"); }
    private WorkspaceEntity workspace(UUID id) { return workspaces.findById(id).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + id)); }
}
