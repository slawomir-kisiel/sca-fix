package pl.sca.fix.cli;

import java.util.Objects;
import pl.sca.fix.core.workspace.WorkspaceState;

/** Stable terminal summary for one pipeline invocation. */
public record CliPipelineSummary(String workspaceId, WorkspaceState.Status status,
                                 WorkspaceState.Stage stage, int completedStages,
                                 int failedStages, int warningCount, int errorCount) {
    public static CliPipelineSummary from(WorkspaceState state) {
        Objects.requireNonNull(state, "state must not be null");
        int completed = (int) state.stages().stream().filter(item -> item.status() == WorkspaceState.StageStatus.COMPLETED).count();
        int failed = (int) state.stages().stream().filter(item -> item.status() == WorkspaceState.StageStatus.FAILED).count();
        return new CliPipelineSummary(state.workspaceId(), state.status(), state.currentStage(), completed,
                failed, state.warnings().size(), state.errors().size());
    }

    public int exitCode() {
        if (status == WorkspaceState.Status.FAILED) return 2;
        if (warningCount > 0 || failedStages > 0) return 1;
        return 0;
    }
}
