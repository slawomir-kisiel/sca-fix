package pl.sca.fix.core.pipeline;

import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.workspace.WorkspaceState;

public record PipelineStageResult(List<String> artifactPaths, List<WorkspaceState.Issue> warnings,
                                  List<WorkspaceState.Issue> errors) {
    public PipelineStageResult {
        artifactPaths = List.copyOf(artifactPaths == null ? List.of() : artifactPaths);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        errors = List.copyOf(errors == null ? List.of() : errors);
    }

    public static PipelineStageResult completed(String... artifacts) {
        return new PipelineStageResult(List.of(artifacts), List.of(), List.of());
    }

    public static PipelineStageResult failed(String code, String message) {
        return new PipelineStageResult(List.of(), List.of(),
                List.of(new WorkspaceState.Issue(Objects.requireNonNull(code), Objects.requireNonNull(message), null,
                        java.time.Instant.now())));
    }
}
