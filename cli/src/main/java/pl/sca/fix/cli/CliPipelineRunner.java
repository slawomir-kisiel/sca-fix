package pl.sca.fix.cli;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.pipeline.PipelineMode;
import pl.sca.fix.core.pipeline.PipelineRequest;
import pl.sca.fix.core.pipeline.ScaFixPipeline;
import pl.sca.fix.core.plan.PlanDocument;
import pl.sca.fix.core.plan.PlanDocumentJsonCodec;
import pl.sca.fix.core.plan.PlanItem;
import pl.sca.fix.core.workspace.WorkspaceState;

/** CLI orchestration facade. It maps parser output to the backend-independent core pipeline. */
public final class CliPipelineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CliPipelineRunner.class);
    private final ScaFixPipeline pipeline;
    private final Consumer<String> output;

    public CliPipelineRunner(ScaFixPipeline pipeline) {
        this(pipeline, System.out::println);
    }

    public CliPipelineRunner(ScaFixPipeline pipeline, Consumer<String> output) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline must not be null");
        this.output = Objects.requireNonNull(output, "output must not be null");
    }

    public CliRunResult run(CliConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        long startedAt = System.nanoTime();
        if (configuration.repository() == null || configuration.repository().isBlank()) {
            throw new CliConfigException("REPOSITORY_REQUIRED", "repository",
                    "A repository is required to execute the pipeline");
        }
        PipelineMode mode = switch (configuration.scanMode()) {
            case NEW -> PipelineMode.NEW;
            case CONTINUE -> PipelineMode.CONTINUE;
            case RESTART -> PipelineMode.RESTART;
        };
        boolean standaloneOverrideReview = configuration.command() == CliCommand.OVERRIDE_REVIEW;
        boolean apply = configuration.command() == CliCommand.APPLY || configuration.apply();
        boolean reportAvailable = configuration.reportSource() != null;
        PipelineRequest request = new PipelineRequest(configuration.workspaceId(),
                Path.of(configuration.repository()), configuration.workspaceRoot(), configuration.pomsRoot(),
                configuration.projects(), configuration.updateLevel(), mode,
                configuration.overrideReview() || standaloneOverrideReview, reportAvailable, apply,
                configuration.commit(), configuration.pushBranch() != null, configuration.ignoreLocalChanges(),
                configuration.proxyHost(), configuration.proxyPort(), configuration.proxyUsername(), configuration.proxyPassword(),
                configuration.disabledStrategies(), configuration.skipLibraries(), configuration.cveOverrideUpdateLevel());

        LOG.info("Starting CLI pipeline command={} mode={} workspaceId={}", configuration.command(), mode,
                configuration.workspaceId());
        output.accept("Starting " + configuration.command().name().toLowerCase() + " pipeline");
        WorkspaceState state = pipeline.run(request, progress ->
                output.accept(PlanTableRenderer.renderStageHeader(progress)));
        CliPipelineSummary summary = CliPipelineSummary.from(state);
        output.accept(render(summary));
        renderPlan(configuration, summary.workspaceId());
        String warnings = new PlanTableRenderer().renderWarnings(state.warnings());
        if (!warnings.isBlank()) output.accept(warnings);
        output.accept("Completed in: " + formatDuration(System.nanoTime() - startedAt));
        LOG.info("Finished CLI pipeline workspaceId={} status={} exitCode={}", summary.workspaceId(),
                summary.status(), summary.exitCode());
        return new CliRunResult(state, summary);
    }

    private void renderPlan(CliConfiguration configuration, String workspaceId) {
        Path plan = configuration.plan() == null
                ? configuration.workspaceRoot().resolve(workspaceId).resolve("plan.json")
                : Path.of(configuration.plan());
        if (!Files.isRegularFile(plan)) return;
        try {
            PlanDocument document = new PlanDocumentJsonCodec().read(Files.readAllBytes(plan));
            List<PlanTableRenderer.PlanRow> rows = document.items().stream().map(this::row).toList();
            if (!rows.isEmpty()) output.accept(new PlanTableRenderer().render(rows));
        } catch (RuntimeException | java.io.IOException exception) {
            LOG.warn("Unable to render plan summary path={} message={}", plan, exception.getMessage());
        }
    }

    private PlanTableRenderer.PlanRow row(PlanItem item) {
        return new PlanTableRenderer.PlanRow(item.artifact().groupId() + ":" + item.artifact().artifactId(),
                item.currentVersion(), item.targetVersion(), item.project().artifactId(), item.strategy(), item.status());
    }

    private static String render(CliPipelineSummary summary) {
        return "Workspace " + summary.workspaceId() + " | status=" + summary.status()
                + " | stage=" + summary.stage() + " | completed=" + summary.completedStages()
                + " | failed=" + summary.failedStages() + " | warnings=" + summary.warningCount()
                + " | errors=" + summary.errorCount();
    }

    static String formatDuration(long elapsedNanos) {
        long seconds = Math.max(0, elapsedNanos / 1_000_000_000L);
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        if (minutes > 0) return minutes + " min " + remainder + " sec";
        return remainder + " sec";
    }

    public record CliRunResult(WorkspaceState state, CliPipelineSummary summary) { }
}
