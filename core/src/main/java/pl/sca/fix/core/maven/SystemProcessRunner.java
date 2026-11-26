package pl.sca.fix.core.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.ProcessRequest;
import pl.sca.fix.core.port.ProcessResult;
import pl.sca.fix.core.port.ProcessRunnerPort;

/** Local process adapter used only by the command-line preview. */
public final class SystemProcessRunner implements ProcessRunnerPort {
    private final boolean verbose;

    public SystemProcessRunner() {
        this(false);
    }

    public SystemProcessRunner(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public ProcessResult run(ProcessRequest request) {
        Instant started = Instant.now();
        if (verbose) {
            System.err.println("[verbose] $ " + safeDisplayCommand(request));
            System.err.println("[verbose] cwd: " + request.workingDirectory());
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(request.command())
                    .directory(request.workingDirectory().toFile());
            builder.environment().putAll(request.environment());
            Process process = builder.start();
            var stdout = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> read(process.getInputStream()));
            var stderr = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> read(process.getErrorStream()));
            boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                ProcessResult result = new ProcessResult(-1, stdout.join(), stderr.join(),
                        Duration.between(started, Instant.now()), true);
                printOutput(result);
                return result;
            }
            ProcessResult result = new ProcessResult(process.exitValue(), stdout.join(), stderr.join(),
                    Duration.between(started, Instant.now()), false);
            printOutput(result);
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PortException(PortException.Kind.IO, "Process execution interrupted", exception);
        } catch (IOException exception) {
                throw new PortException(PortException.Kind.IO, "Unable to start external process: "
                        + safeDisplayCommand(request), exception);
        } catch (RuntimeException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to read process output", exception);
        }
    }

    private void printOutput(ProcessResult result) {
        if (!verbose) return;
        print(System.out, "stdout", result.stdout());
        print(System.err, "stderr", result.stderr());
        System.err.println("[verbose] exitCode=" + result.exitCode() + " timedOut=" + result.timedOut());
    }

    private static void print(java.io.PrintStream stream, String name, String value) {
        if (value == null || value.isEmpty()) return;
        stream.println("[verbose] " + name + ":");
        stream.print(value);
        if (!value.endsWith("\n") && !value.endsWith("\r")) stream.println();
    }

    private static String read(java.io.InputStream input) {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to read process output", exception);
        }
    }

    private static String safeDisplayCommand(ProcessRequest request) {
        return request.displayCommand().replaceAll("(?i)(-DnvdApiKey=)[^\\s]+", "$1***");
    }
}
