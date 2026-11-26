package pl.sca.fix.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import pl.sca.fix.core.pipeline.ScaFixPipeline;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.workspace.FileWorkspaceStore;
import pl.sca.fix.core.workspace.WorkspaceStateJsonCodec;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.maven.SystemProcessRunner;

/** Production entry point for the standalone executable CLI. */
public final class CliApplication {
    private static final Logger LOG = LoggerFactory.getLogger(CliApplication.class);

    private CliApplication() { }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) System.exit(exitCode);
    }

    static int run(String[] args) {
        boolean verbose = contains(args, "--verbose");
        try {
            if (args == null || args.length == 0 || contains(args, "--help") || contains(args, "-h")) {
                try {
                    CliConfigurationPreview.main(new String[]{"--help"});
                } catch (Exception exception) {
                    throw new IllegalStateException("Unable to render CLI help", exception);
                }
                return 0;
            }
            if (contains(args, "--version")) {
                System.out.println("sca-fix-cli 0.1.0-SNAPSHOT");
                return 0;
            }
            CliConfiguration configuration = new CliConfigurationParser().parse(args);
            if (configuration == null) return 0;
            verbose = configuration.verbose();
            SystemProcessRunner processes = new SystemProcessRunner(verbose);
            NioFileSystemAdapter files = new NioFileSystemAdapter();
            FileWorkspaceStore store = new FileWorkspaceStore(configuration.workspaceRoot(), files,
                    new WorkspaceStateJsonCodec());
            CliRuntimeStageExecutor executor = new CliRuntimeStageExecutor(configuration, processes);
            ScaFixPipeline pipeline = new ScaFixPipeline(store, executor,
                    new pl.sca.fix.core.git.GitProcessAdapter(processes), Clock.systemUTC(),
                    verbose ? exception -> exception.printStackTrace(System.err) : exception -> { });
            CliPipelineRunner.CliRunResult result = new CliPipelineRunner(pipeline).run(configuration);
            return result.summary().exitCode();
        } catch (CliConfigException exception) {
            System.err.println(exception.code() + " [" + exception.parameter() + "]: " + exception.getMessage());
            return 2;
        } catch (PortException exception) {
            LOG.error("CLI pipeline failed kind={} message={}", exception.kind(), exception.getMessage(), exception);
            System.err.println("Pipeline failed [" + exception.kind() + "]: " + exception.getMessage());
            if (verbose) exception.printStackTrace(System.err);
            return 2;
        } catch (RuntimeException exception) {
            LOG.error("CLI pipeline failed message={}", exception.getMessage(), exception);
            System.err.println("Pipeline failed: " + safeMessage(exception));
            if (verbose) exception.printStackTrace(System.err);
            return 2;
        }
    }

    private static boolean contains(String[] args, String expected) {
        for (String arg : args) if (expected.equals(arg)) return true;
        return false;
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
