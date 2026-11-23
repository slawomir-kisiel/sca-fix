package pl.sca.fix.uibackend.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Invalidates persisted outputs from a selected stage and all dependent stages. */
@Service
public class StageRestartService {
    private static final List<String> STAGES = List.of("CONFIGURATION", "PROJECT_SCOPE", "MAVEN_ANALYSIS", "REPORT", "CORRELATION", "OCCURRENCES", "STRATEGIES", "PLAN", "APPLY");
    private final WorkspaceRepository workspaces;
    private final HistoryService history;
    public StageRestartService(WorkspaceRepository workspaces, HistoryService history) { this.workspaces = workspaces; this.history = history; }

    public RestartResult restart(UUID workspaceId, String stage, boolean confirmed) {
        WorkspaceEntity workspace = workspaces.findById(workspaceId).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId));
        String normalized = stage == null ? "" : stage.trim().toUpperCase();
        if (!STAGES.contains(normalized)) throw new WorkspaceException("UNKNOWN_STAGE", "Unknown restart stage: " + stage);
        if (!confirmed) throw new WorkspaceException("RESTART_CONFIRMATION_REQUIRED", "Restart requires explicit confirmation");
        Path root = Path.of(workspace.getRepositoryPath()).resolve("target/scafix");
        List<Path> targets = targets(root, normalized);
        List<String> removed = new ArrayList<>();
        try {
            for (Path target : targets) if (Files.exists(target)) { deleteTree(target); removed.add(root.relativize(target).toString()); }
            history.append(workspaceId, normalized, "RESTART", String.join(", ", removed));
            return new RestartResult(workspaceId, normalized, removed, "READY_TO_RERUN");
        } catch (IOException exception) { throw new WorkspaceException("STAGE_RESTART_FAILED", exception.getMessage()); }
    }
    private static List<Path> targets(Path root, String stage) {
        List<Path> result = new ArrayList<>();
        switch (stage) {
            case "CONFIGURATION" -> { result.add(root.resolve("ui-analysis")); result.add(root.resolve("reports")); }
            case "PROJECT_SCOPE", "MAVEN_ANALYSIS" -> { result.add(root.resolve("ui-analysis")); result.addAll(reportOutputs(root)); }
            case "REPORT" -> { result.add(root.resolve("reports/correlation.json")); result.add(root.resolve("reports/occurrence-decisions.json")); result.add(root.resolve("reports/strategy-decisions.json")); result.add(root.resolve("reports/plan.json")); }
            case "CORRELATION" -> { result.add(root.resolve("reports/occurrence-decisions.json")); result.add(root.resolve("reports/strategy-decisions.json")); result.add(root.resolve("reports/plan.json")); }
            case "OCCURRENCES" -> { result.add(root.resolve("reports/strategy-decisions.json")); result.add(root.resolve("reports/plan.json")); }
            case "STRATEGIES", "PLAN", "APPLY" -> result.add(root.resolve("reports/plan.json"));
            default -> { }
        }
        return result;
    }
    private static List<Path> reportOutputs(Path root) { return List.of(root.resolve("reports/correlation.json"), root.resolve("reports/occurrence-decisions.json"), root.resolve("reports/strategy-decisions.json"), root.resolve("reports/plan.json")); }
    private static void deleteTree(Path path) throws IOException { if (Files.isDirectory(path)) try (var stream = Files.list(path)) { for (Path child : stream.toList()) deleteTree(child); } Files.deleteIfExists(path); }
    public record RestartResult(UUID workspaceId, String stage, List<String> removedOutputs, String nextStatus) { }
}
