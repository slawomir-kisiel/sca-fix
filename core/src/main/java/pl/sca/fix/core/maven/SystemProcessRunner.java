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
    @Override
    public ProcessResult run(ProcessRequest request) {
        Instant started = Instant.now();
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
                return new ProcessResult(-1, stdout.join(), stderr.join(), Duration.between(started, Instant.now()), true);
            }
            return new ProcessResult(process.exitValue(), stdout.join(), stderr.join(),
                    Duration.between(started, Instant.now()), false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PortException(PortException.Kind.IO, "Process execution interrupted", exception);
        } catch (IOException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to start external process: "
                    + request.displayCommand(), exception);
        } catch (RuntimeException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to read process output", exception);
        }
    }

    private static String read(java.io.InputStream input) {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to read process output", exception);
        }
    }
}
