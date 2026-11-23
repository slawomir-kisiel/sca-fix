package pl.sca.fix.uibackend.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.plan.PlanDocument;
import pl.sca.fix.core.plan.PlanDocumentJsonCodec;
import pl.sca.fix.core.plan.PlanItem;

@Service
public class PlanService {
    private final WorkspaceRepository workspaces;
    private final PlanDocumentJsonCodec codec = new PlanDocumentJsonCodec();

    public PlanService(WorkspaceRepository workspaces) { this.workspaces = workspaces; }

    public PlanDocument get(UUID workspaceId) { return read(workspace(workspaceId)); }

    public PlanDocument updateItem(UUID workspaceId, String planId, String itemId, PlanItemRequest request) {
        WorkspaceEntity workspace = workspace(workspaceId); PlanDocument plan = read(workspace);
        if (!plan.planId().equals(planId)) throw new WorkspaceException("PLAN_NOT_FOUND", "Plan not found: " + planId);
        PlanItem current = plan.items().stream().filter(item -> item.itemId().equals(itemId)).findFirst()
                .orElseThrow(() -> new WorkspaceException("PLAN_ITEM_NOT_FOUND", "PlanItem not found: " + itemId));
        PlanItem updated = new PlanItem(current.itemId(), current.project(), current.artifact(), current.currentVersion(),
                request.targetVersion() == null || request.targetVersion().isBlank() ? current.targetVersion() : request.targetVersion(),
                request.strategy() == null || request.strategy().isBlank() ? current.strategy() : request.strategy(),
                current.finding(), current.pomSha256(), current.baseCommitSha(),
                request.status() == null || request.status().isBlank() ? current.status() : request.status(),
                current.warnings(), current.errors(), current.details(), current.errorMessage());
        PlanDocument result = new PlanDocument(plan.schemaVersion(), plan.planId(), plan.createdAt(), plan.repository(),
                plan.updateLevel(), plan.items().stream().map(item -> item.itemId().equals(itemId) ? updated : item).toList(),
                plan.warnings(), plan.errors(), plan.status());
        save(workspace, result); return result;
    }

    private PlanDocument read(WorkspaceEntity workspace) {
        try { Path path = planPath(workspace); if (!Files.exists(path)) throw new WorkspaceException("PLAN_NOT_FOUND", "No plan exists for workspace"); return codec.read(Files.readAllBytes(path)); }
        catch (WorkspaceException exception) { throw exception; }
        catch (Exception exception) { throw new WorkspaceException("PLAN_READ_FAILED", exception.getMessage()); }
    }
    private void save(WorkspaceEntity workspace, PlanDocument plan) { try { Files.write(planPath(workspace), codec.write(plan)); } catch (Exception exception) { throw new WorkspaceException("PLAN_WRITE_FAILED", exception.getMessage()); } }
    private static Path planPath(WorkspaceEntity workspace) { return Path.of(workspace.getRepositoryPath()).resolve("target/scafix/reports/plan.json"); }
    private WorkspaceEntity workspace(UUID id) { return workspaces.findById(id).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + id)); }
    public record PlanItemRequest(String strategy, String targetVersion, String status) {}
}
