package pl.sca.fix.core.pipeline;

import pl.sca.fix.core.workspace.WorkspaceState;

/** Executes one already selected pipeline stage. Implementations adapt core services and ports. */
@FunctionalInterface
public interface PipelineStageExecutor {
    PipelineStageResult execute(WorkspaceState.Stage stage, WorkspaceState state);
}
