package pl.sca.fix.core.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.port.GitBranch;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitStatus;
import pl.sca.fix.core.port.WorkspaceStorePort;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.workspace.WorkspaceState;
import pl.sca.fix.core.pipeline.PipelineConfiguration;

class ScaFixPipelineTest {
    private static final Path REPOSITORY = Path.of("checkout");

    @Test
    void runsFullPipelineAndOverrideReviewAsAnAdditionalStage() {
        MemoryStore store = new MemoryStore();
        List<WorkspaceState.Stage> stages = new ArrayList<>();
        ScaFixPipeline pipeline = new ScaFixPipeline(store, (stage, state) -> {
            stages.add(stage);
            return PipelineStageResult.completed(stage.name().toLowerCase() + ".json");
        }, new FakeGit(), fixedClock());

        WorkspaceState result = pipeline.run(request(PipelineMode.NEW, "ws-1", true, true));

        assertEquals(WorkspaceState.Status.COMPLETED, result.status());
        assertTrue(stages.contains(WorkspaceState.Stage.OVERRIDE_REVIEW));
        assertEquals(List.of("DISCOVERY", "MAVEN_ANALYSIS", "REPORT", "CORRELATION", "OVERRIDE_REVIEW",
                "PLANNING", "SUMMARY"), stages.stream().map(Enum::name).toList());
        assertEquals(WorkspaceState.StageStatus.COMPLETED,
                result.stages().stream().filter(stage -> stage.name().equals("OVERRIDE_REVIEW")).findFirst().orElseThrow().status());
    }

    @Test
    void allowsStandaloneOverrideReviewWithoutReport() {
        MemoryStore store = new MemoryStore();
        List<WorkspaceState.Stage> stages = new ArrayList<>();
        ScaFixPipeline pipeline = new ScaFixPipeline(store, (stage, state) -> {
            stages.add(stage);
            return PipelineStageResult.completed();
        }, new FakeGit(), fixedClock());

        WorkspaceState result = pipeline.run(request(PipelineMode.NEW, "ws-override", false, true));

        assertEquals(WorkspaceState.Status.COMPLETED, result.status());
        assertEquals(List.of(WorkspaceState.Stage.DISCOVERY, WorkspaceState.Stage.MAVEN_ANALYSIS,
                WorkspaceState.Stage.OVERRIDE_REVIEW, WorkspaceState.Stage.SUMMARY), stages);
    }

    @Test
    void continueSkipsCompletedStages() {
        MemoryStore store = new MemoryStore();
        ScaFixPipeline first = new ScaFixPipeline(store, (stage, state) -> PipelineStageResult.completed(),
                new FakeGit(), fixedClock());
        first.run(request(PipelineMode.NEW, "ws-continue", false, true));

        List<WorkspaceState.Stage> retried = new ArrayList<>();
        ScaFixPipeline second = new ScaFixPipeline(store, (stage, state) -> {
            retried.add(stage);
            return PipelineStageResult.completed();
        }, new FakeGit(), fixedClock());
        WorkspaceState result = second.run(request(PipelineMode.CONTINUE, "ws-continue", false, true));

        assertEquals(WorkspaceState.Status.COMPLETED, result.status());
        assertTrue(retried.isEmpty());
    }

    @Test
    void continueLoadsAllExecutionParametersFromWorkspaceConfiguration() {
        MemoryStore store = new MemoryStore();
        ScaFixPipeline first = new ScaFixPipeline(store, (stage, state) -> PipelineStageResult.completed(),
                new FakeGit(), fixedClock());
        first.run(request(PipelineMode.NEW, "ws-config", false, true));

        List<WorkspaceState.Stage> stages = new ArrayList<>();
        ScaFixPipeline continued = new ScaFixPipeline(store, (stage, state) -> {
            stages.add(stage);
            return PipelineStageResult.completed();
        }, new FakeGit(), fixedClock());
        PipelineRequest withoutCommandLineParameters = new PipelineRequest("ws-config", null, null, null,
                List.of(), null, PipelineMode.CONTINUE, false, false, false, false, false, false);

        WorkspaceState result = continued.run(withoutCommandLineParameters);

        assertEquals(WorkspaceState.Status.COMPLETED, result.status());
        assertTrue(stages.isEmpty(), "continue should use the completed stages from the saved configuration");
    }

    private static PipelineRequest request(PipelineMode mode, String workspaceId,
                                           boolean report, boolean overrideReview) {
        return new PipelineRequest(workspaceId, REPOSITORY, Path.of("workspaces"), Path.of("poms"),
                List.of("*"), UpdateLevel.MINOR, mode, overrideReview, report, false, false, false, false);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);
    }

    private static final class MemoryStore implements WorkspaceStorePort {
        private final Map<String, WorkspaceState> states = new HashMap<>();
        private final Map<String, PipelineConfiguration> configurations = new HashMap<>();
        @Override public WorkspaceState load(String workspaceId) { return states.get(workspaceId); }
        @Override public void save(WorkspaceState state) { states.put(state.workspaceId(), state); }
        @Override public void saveConfiguration(String workspaceId, PipelineConfiguration configuration) { configurations.put(workspaceId, configuration); }
        @Override public PipelineConfiguration loadConfiguration(String workspaceId) { return configurations.get(workspaceId); }
        @Override public boolean exists(String workspaceId) { return states.containsKey(workspaceId); }
        @Override public void delete(String workspaceId) { states.remove(workspaceId); }
    }

    private static final class FakeGit implements GitPort {
        @Override public GitStatus status(Path repository) { return new GitStatus(true, true, "commit-1", List.of(), "main"); }
        @Override public List<Path> trackedFiles(Path repository) { return List.of(Path.of("pom.xml")); }
        @Override public void add(Path repository, List<Path> paths) { }
        @Override public GitBranch createBranch(Path repository, String branchName) { return new GitBranch(branchName, "commit-1"); }
        @Override public GitBranch checkoutBranch(Path repository, String branchName) { return new GitBranch(branchName, "commit-1"); }
        @Override public void deleteBranch(Path repository, String branchName) { }
        @Override public String commit(Path repository, String message) { return "commit-2"; }
        @Override public void push(Path repository, String branchName) { }
    }
}
