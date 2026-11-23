package pl.sca.fix.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.pipeline.PipelineConfiguration;
import pl.sca.fix.core.pipeline.PipelineStageResult;
import pl.sca.fix.core.pipeline.ScaFixPipeline;
import pl.sca.fix.core.port.GitBranch;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitStatus;
import pl.sca.fix.core.port.WorkspaceStorePort;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.workspace.WorkspaceState;

class CliPipelineRunnerTest {
    @Test
    void mapsStandaloneOverrideReviewAndRendersSummary() {
        List<WorkspaceState.Stage> stages = new ArrayList<>();
        ScaFixPipeline pipeline = pipeline(stages);
        CliConfiguration configuration = new CliConfiguration(CliCommand.OVERRIDE_REVIEW, ScanMode.NEW, "ws",
                "checkout", List.of("*"), null, null, null, null, UpdateLevel.MINOR, "COMMON_SOURCE",
                "R1d,S10m", false, false, null, false, null, Path.of("workspaces"), Path.of("poms"), false);

        CliPipelineRunner.CliRunResult result = new CliPipelineRunner(pipeline, ignored -> { }).run(configuration);

        assertEquals(WorkspaceState.Status.COMPLETED, result.summary().status());
        assertEquals(List.of(WorkspaceState.Stage.DISCOVERY, WorkspaceState.Stage.MAVEN_ANALYSIS,
                WorkspaceState.Stage.OVERRIDE_REVIEW, WorkspaceState.Stage.SUMMARY), stages);
    }

    @Test
    void rejectsExecutionWithoutRepository() {
        ScaFixPipeline pipeline = pipeline(new ArrayList<>());
        CliConfiguration configuration = new CliConfiguration(CliCommand.APPLY, ScanMode.NEW, null,
                null, List.of("*"), null, null, null, "plan.json", UpdateLevel.MINOR, "COMMON_SOURCE",
                "R1d,S10m", false, false, null, false, null, Path.of("workspaces"), Path.of("poms"), false);

        assertEquals("REPOSITORY_REQUIRED", assertThrows(CliConfigException.class,
                () -> new CliPipelineRunner(pipeline).run(configuration)).code());
    }

    @Test
    void formatsElapsedPipelineTime() {
        assertEquals("2 min 34 sec", CliPipelineRunner.formatDuration(154_000_000_000L));
        assertEquals("0 sec", CliPipelineRunner.formatDuration(999_000_000L));
    }

    private static ScaFixPipeline pipeline(List<WorkspaceState.Stage> stages) {
        return new ScaFixPipeline(new MemoryStore(), (stage, state) -> {
            stages.add(stage);
            return PipelineStageResult.completed();
        }, new FakeGit(), Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC));
    }

    private static final class MemoryStore implements WorkspaceStorePort {
        private final Map<String, WorkspaceState> states = new HashMap<>();
        private final Map<String, PipelineConfiguration> configurations = new HashMap<>();
        public WorkspaceState load(String id) { return states.get(id); }
        public void save(WorkspaceState state) { states.put(state.workspaceId(), state); }
        public void saveConfiguration(String id, PipelineConfiguration config) { configurations.put(id, config); }
        public PipelineConfiguration loadConfiguration(String id) { return configurations.get(id); }
        public boolean exists(String id) { return states.containsKey(id); }
        public void delete(String id) { states.remove(id); configurations.remove(id); }
    }

    private static final class FakeGit implements GitPort {
        public GitStatus status(Path repository) { return new GitStatus(true, true, "commit-1", List.of(), "main"); }
        public List<Path> trackedFiles(Path repository) { return List.of(Path.of("pom.xml")); }
        public void add(Path repository, List<Path> paths) { }
        public GitBranch createBranch(Path repository, String branchName) { return new GitBranch(branchName, "commit-1"); }
        public GitBranch checkoutBranch(Path repository, String branchName) { return new GitBranch(branchName, "commit-1"); }
        public void deleteBranch(Path repository, String branchName) { }
        public String commit(Path repository, String message) { return "commit-2"; }
        public void push(Path repository, String branchName) { }
    }
}
