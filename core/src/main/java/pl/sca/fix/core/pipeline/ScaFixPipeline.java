package pl.sca.fix.core.pipeline;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitStatus;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.WorkspaceStorePort;
import pl.sca.fix.core.workspace.WorkspaceState;

/** Coordinates the ordered CLI workflow and persists every stage transition. */
public final class ScaFixPipeline {
    private static final Logger LOG = LoggerFactory.getLogger(ScaFixPipeline.class);
    private static final List<WorkspaceState.Stage> ORDER = List.of(
            WorkspaceState.Stage.DISCOVERY, WorkspaceState.Stage.MAVEN_ANALYSIS,
            WorkspaceState.Stage.REPORT, WorkspaceState.Stage.CORRELATION,
            WorkspaceState.Stage.OVERRIDE_REVIEW, WorkspaceState.Stage.PLANNING,
            WorkspaceState.Stage.APPLY, WorkspaceState.Stage.PUBLISH, WorkspaceState.Stage.SUMMARY);

    private final WorkspaceStorePort store;
    private final PipelineStageExecutor executor;
    private final GitPort git;
    private final Clock clock;

    public ScaFixPipeline(WorkspaceStorePort store, PipelineStageExecutor executor, GitPort git) {
        this(store, executor, git, Clock.systemUTC());
    }

    public ScaFixPipeline(WorkspaceStorePort store, PipelineStageExecutor executor, GitPort git, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.git = Objects.requireNonNull(git, "git must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public WorkspaceState run(PipelineRequest request) {
        return run(request, ignored -> { });
    }

    /** Runs the pipeline and notifies the caller immediately before each executed stage. */
    public WorkspaceState run(PipelineRequest request, Consumer<StageProgress> stageListener) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(stageListener, "stageListener must not be null");
        PipelineRequest effectiveRequest = request;
        WorkspaceState state = loadOrCreate(request);
        if (request.mode() == PipelineMode.CONTINUE) {
            PipelineConfiguration savedConfiguration = store.loadConfiguration(request.workspaceId());
            if (savedConfiguration == null) {
                throw new PortException(PortException.Kind.NOT_FOUND,
                        "Workspace configuration not found: " + request.workspaceId());
            }
            effectiveRequest = savedConfiguration.toRequest(request.workspaceId(), PipelineMode.CONTINUE);
            // Explicit proxy options supplied on continue/config must override the snapshot.
            if (request.proxyHost() != null || request.proxyPort() != null || request.proxyUsername() != null
                    || request.proxyPassword() != null) {
                effectiveRequest = new PipelineRequest(request.workspaceId(), effectiveRequest.repository(),
                        effectiveRequest.workspaceRoot(), effectiveRequest.pomsRoot(), effectiveRequest.projects(),
                        effectiveRequest.updateLevel(), PipelineMode.CONTINUE, effectiveRequest.overrideReview(),
                        effectiveRequest.reportAvailable(), effectiveRequest.apply(), effectiveRequest.commit(),
                        effectiveRequest.push(), effectiveRequest.ignoreLocalChanges(), request.proxyHost(),
                        request.proxyPort(), request.proxyUsername(), request.proxyPassword(), request.disabledStrategies(),
                        effectiveRequest.skipLibraries(), effectiveRequest.cveOverrideUpdateLevel());
            }
        } else {
            store.saveConfiguration(state.workspaceId(), PipelineConfiguration.from(request));
        }
        validateRepository(effectiveRequest, state);
        state = update(state, WorkspaceState.Status.RUNNING, WorkspaceState.Stage.DISCOVERY, List.of(), List.of());
        store.save(state);
        List<WorkspaceState.Stage> selectedStages = selectedStages(effectiveRequest);
        for (int stageIndex = 0; stageIndex < selectedStages.size(); stageIndex++) {
            WorkspaceState.Stage stage = selectedStages.get(stageIndex);
            WorkspaceState.StageState previous = stageState(state, stage);
            if (request.mode() == PipelineMode.CONTINUE && previous.status() == WorkspaceState.StageStatus.COMPLETED) {
                LOG.info("Skipping completed pipeline stage workspaceId={} stage={}", state.workspaceId(), stage);
                continue;
            }
            stageListener.accept(new StageProgress(stageIndex + 1, selectedStages.size(), stage));
            state = executeStage(state, stage);
            if (state.status() == WorkspaceState.Status.FAILED) return state;
        }
        state = update(state, WorkspaceState.Status.COMPLETED, WorkspaceState.Stage.SUMMARY,
                List.of(), List.of());
        store.save(state);
        LOG.info("Finished pipeline workspaceId={} status={}", state.workspaceId(), state.status());
        return state;
    }

    public record StageProgress(int number, int total, WorkspaceState.Stage stage) { }

    private WorkspaceState executeStage(WorkspaceState state, WorkspaceState.Stage stage) {
        Instant started = clock.instant();
        WorkspaceState running = replaceStage(state, stage,
                new WorkspaceState.StageState(stage.name(), WorkspaceState.StageStatus.RUNNING, started, null,
                        stageState(state, stage).attempt() + 1, stageState(state, stage).artifactPaths(), null, null));
        store.save(running);
        try {
            LOG.info("Starting pipeline stage workspaceId={} stage={}", state.workspaceId(), stage);
            PipelineStageResult result = executor.execute(stage, running);
            if (!result.errors().isEmpty()) return failed(running, stage, result, started);
            WorkspaceState.StageState done = new WorkspaceState.StageState(stage.name(),
                    WorkspaceState.StageStatus.COMPLETED, started, clock.instant(),
                    stageState(running, stage).attempt(), result.artifactPaths(), null, null);
            WorkspaceState completed = replaceStage(running, stage, done);
            completed = appendIssues(completed, result.warnings(), List.of());
            store.save(completed);
            LOG.info("Finished pipeline stage workspaceId={} stage={} status=COMPLETED", state.workspaceId(), stage);
            return completed;
        } catch (RuntimeException exception) {
            PipelineStageResult result = PipelineStageResult.failed("PIPELINE_STAGE_FAILED", safeMessage(exception));
            return failed(running, stage, result, started);
        }
    }

    private WorkspaceState failed(WorkspaceState state, WorkspaceState.Stage stage,
                                   PipelineStageResult result, Instant started) {
        WorkspaceState.Issue issue = result.errors().get(0);
        WorkspaceState.StageState failed = new WorkspaceState.StageState(stage.name(),
                WorkspaceState.StageStatus.FAILED, started, clock.instant(), stageState(state, stage).attempt(),
                result.artifactPaths(), issue.reason(), issue.message());
        WorkspaceState updated = replaceStage(state, stage, failed);
        updated = appendIssues(updated, result.warnings(), result.errors());
        updated = update(updated, WorkspaceState.Status.FAILED, stage, List.of(), List.of());
        store.save(updated);
        LOG.error("Pipeline stage failed workspaceId={} stage={} error={}", state.workspaceId(), stage, issue.message());
        return updated;
    }

    private WorkspaceState loadOrCreate(PipelineRequest request) {
        if (request.mode() == PipelineMode.CONTINUE) return store.load(request.workspaceId());
        if (request.mode() == PipelineMode.RESTART) {
            WorkspaceState previous = store.load(request.workspaceId());
            String id = request.workspaceId() + "-restart-" + UUID.randomUUID().toString().substring(0, 8);
            return initial(request, id, previous.workspaceId());
        }
        String id = request.workspaceId() == null || request.workspaceId().isBlank()
                ? UUID.randomUUID().toString() : request.workspaceId();
        return initial(request, id, null);
    }

    private WorkspaceState initial(PipelineRequest request, String id, String restartedFrom) {
        Instant now = clock.instant();
        GitStatus gitStatus = git.status(request.repository());
        String commit = gitStatus == null ? null : gitStatus.commitSha();
        List<WorkspaceState.StageState> stages = ORDER.stream().map(stage ->
                new WorkspaceState.StageState(stage.name(), WorkspaceState.StageStatus.PENDING, null, null, 0,
                        List.of(), null, null)).toList();
        return new WorkspaceState(1, id, now, now, WorkspaceState.Status.NEW,
                WorkspaceState.Stage.DISCOVERY,
                new WorkspaceState.Configuration(request.repository().toString(), request.pomsRoot().toString(),
                        request.projects(), request.ignoreLocalChanges(), request.updateLevel().name()),
                List.of(new WorkspaceState.Repository(request.repository().toString(), null, commit)), List.of(),
                new WorkspaceState.Artifacts(null, null, List.of()), stages,
                restartedFrom == null ? List.of() : List.of(new WorkspaceState.Issue(
                        "RESTARTED_FROM", restartedFrom, "DISCOVERY", now)), List.of());
    }

    private void validateRepository(PipelineRequest request, WorkspaceState state) {
        GitStatus status = git.status(request.repository());
        if (status == null || !status.repository()) throw new PortException(PortException.Kind.NOT_FOUND,
                "Not a Git repository: " + request.repository());
        if (state.repositories().isEmpty() || state.repositories().get(0).baseCommitSha() == null) return;
        if (!state.repositories().get(0).baseCommitSha().equals(status.commitSha())
                && request.mode() != PipelineMode.RESTART)
            throw new PortException(PortException.Kind.CONFLICT, "Repository commit changed for workspace " + state.workspaceId());
    }

    private List<WorkspaceState.Stage> selectedStages(PipelineRequest request) {
        List<WorkspaceState.Stage> result = new ArrayList<>();
        result.add(WorkspaceState.Stage.DISCOVERY);
        result.add(WorkspaceState.Stage.MAVEN_ANALYSIS);
        if (request.reportAvailable()) result.addAll(List.of(WorkspaceState.Stage.REPORT,
                WorkspaceState.Stage.CORRELATION));
        if (request.overrideReview()) result.add(WorkspaceState.Stage.OVERRIDE_REVIEW);
        if (request.reportAvailable()) result.add(WorkspaceState.Stage.PLANNING);
        if (request.apply()) result.add(WorkspaceState.Stage.APPLY);
        if (request.commit() || request.push()) result.add(WorkspaceState.Stage.PUBLISH);
        result.add(WorkspaceState.Stage.SUMMARY);
        return result;
    }

    private WorkspaceState.StageState stageState(WorkspaceState state, WorkspaceState.Stage stage) {
        return state.stages().stream().filter(item -> item.name().equals(stage.name())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pipeline stage: " + stage));
    }

    private WorkspaceState replaceStage(WorkspaceState state, WorkspaceState.Stage stage,
                                        WorkspaceState.StageState replacement) {
        List<WorkspaceState.StageState> stages = state.stages().stream()
                .map(item -> item.name().equals(stage.name()) ? replacement : item).toList();
        return copy(state, state.status(), stage, stages, state.warnings(), state.errors());
    }

    private WorkspaceState appendIssues(WorkspaceState state, List<WorkspaceState.Issue> warnings,
                                        List<WorkspaceState.Issue> errors) {
        List<WorkspaceState.Issue> allWarnings = new ArrayList<>(state.warnings());
        warnings.forEach(issue -> {
            if (!allWarnings.contains(issue)) allWarnings.add(issue);
        });
        List<WorkspaceState.Issue> allErrors = new ArrayList<>(state.errors());
        errors.forEach(issue -> { if (!allErrors.contains(issue)) allErrors.add(issue); });
        return copy(state, state.status(), state.currentStage(), state.stages(), allWarnings, allErrors);
    }

    private WorkspaceState update(WorkspaceState state, WorkspaceState.Status status, WorkspaceState.Stage stage,
                                  List<WorkspaceState.Issue> warnings, List<WorkspaceState.Issue> errors) {
        return copy(state, status, stage, state.stages(), warnings.isEmpty() ? state.warnings() : warnings,
                errors.isEmpty() ? state.errors() : errors);
    }

    private WorkspaceState copy(WorkspaceState state, WorkspaceState.Status status, WorkspaceState.Stage stage,
                                List<WorkspaceState.StageState> stages, List<WorkspaceState.Issue> warnings,
                                List<WorkspaceState.Issue> errors) {
        return new WorkspaceState(state.schemaVersion(), state.workspaceId(), state.createdAt(), clock.instant(),
                status, stage, state.configuration(), state.repositories(), state.projects(), state.artifacts(),
                stages, warnings, errors);
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
