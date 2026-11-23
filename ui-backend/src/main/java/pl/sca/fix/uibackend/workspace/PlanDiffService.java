package pl.sca.fix.uibackend.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.plan.PlanDocument;
import pl.sca.fix.core.plan.PlanDocumentJsonCodec;
import pl.sca.fix.core.plan.PlanItem;
import pl.sca.fix.core.plan.PomChange;
import pl.sca.fix.core.plan.PomEditRequest;
import pl.sca.fix.core.plan.PomEditor;

/** Creates a read-only preview of all changes in a workspace plan. */
@Service
public class PlanDiffService {
    private final WorkspaceRepository workspaces;
    private final PlanDocumentJsonCodec codec = new PlanDocumentJsonCodec();
    private final PomEditor editor = new PomEditor(new NioFileSystemAdapter());

    public PlanDiffService(WorkspaceRepository workspaces) { this.workspaces = workspaces; }

    public PlanDiff get(UUID workspaceId, String planId) {
        WorkspaceEntity workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId));
        try {
            Path planPath = repository(workspace).resolve("target/scafix/reports/plan.json");
            PlanDocument plan = codec.read(java.nio.file.Files.readAllBytes(planPath));
            if (!plan.planId().equals(planId)) throw new WorkspaceException("PLAN_NOT_FOUND", "Plan not found: " + planId);
            List<PlanDiffItem> items = new ArrayList<>();
            StringBuilder combined = new StringBuilder();
            for (PlanItem item : plan.items()) {
                try {
                    PomChange change = editor.prepare(toRequest(item, repository(workspace)));
                    items.add(new PlanDiffItem(item.itemId(), item.project().pomPath(), item.artifact().groupId() + ":" + item.artifact().artifactId(),
                            item.strategy(), item.currentVersion(), item.targetVersion(), change.changed(), change.diff(), null));
                    if (!change.diff().isBlank()) combined.append("# ").append(item.itemId()).append(' ').append(item.project().pomPath()).append('\n').append(change.diff());
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                    items.add(new PlanDiffItem(item.itemId(), item.project().pomPath(), item.artifact().groupId() + ":" + item.artifact().artifactId(),
                            item.strategy(), item.currentVersion(), item.targetVersion(), false, "", message));
                }
            }
            return new PlanDiff(plan.planId(), items, combined.toString());
        } catch (WorkspaceException exception) { throw exception; }
        catch (Exception exception) { throw new WorkspaceException("PLAN_DIFF_FAILED", exception.getMessage()); }
    }

    private static PomEditRequest toRequest(PlanItem item, Path repository) {
        Path pom = repository.resolve(Path.of(item.project().pomPath()).normalize()).normalize();
        return new PomEditRequest(pom, item.strategy(), item.artifact().groupId(), item.artifact().artifactId(),
                item.currentVersion(), item.targetVersion(), item.details().get("propertyName"), item.details().get("scope"),
                item.details().get("type"), item.details().get("classifier"), item.details().getOrDefault("operation", "ADD_OR_UPDATE"),
                item.details(), null);
    }

    private static Path repository(WorkspaceEntity workspace) { return Path.of(workspace.getRepositoryPath()).toAbsolutePath().normalize(); }

    public record PlanDiff(String planId, List<PlanDiffItem> items, String combinedDiff) { }
    public record PlanDiffItem(String itemId, String pomPath, String artifact, String strategy, String currentVersion,
                               String targetVersion, boolean changed, String diff, String errorMessage) { }
}
