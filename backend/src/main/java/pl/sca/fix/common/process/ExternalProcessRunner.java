package pl.sca.fix.common.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Minimal process port; adapters for Git and Maven build on this component. */
@Component
public class ExternalProcessRunner {
    private static final Logger log = LoggerFactory.getLogger(ExternalProcessRunner.class);
    private final Map<UUID, Process> runningProcesses = new ConcurrentHashMap<>();

    public CompletableFuture<Integer> run(List<String> command, Path workingDirectory,
                                          LineConsumer stdout, LineConsumer stderr) {
        return run(UUID.randomUUID(), command, workingDirectory, stdout, stderr);
    }

    public CompletableFuture<Integer> run(UUID executionId, List<String> command, Path workingDirectory,
                                          LineConsumer stdout, LineConsumer stderr) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting external process executionId={} command={} workingDirectory={}", executionId, command, workingDirectory);
                List<String> effectiveCommand = adaptCommandForCurrentOs(command);
                log.debug("Effective external process command executionId={} command={}", executionId, effectiveCommand);
                Process process = new ProcessBuilder(effectiveCommand).directory(workingDirectory.toFile()).start();
                runningProcesses.put(executionId, process);
                CompletableFuture<Void> out = CompletableFuture.runAsync(() -> process.inputReader().lines().forEach(stdout::accept));
                CompletableFuture<Void> err = CompletableFuture.runAsync(() -> process.errorReader().lines().forEach(stderr::accept));
                int exitCode = process.waitFor();
                CompletableFuture.allOf(out, err).join();
                log.info("Finished external process executionId={} exitCode={}", executionId, exitCode);
                return exitCode;
            } catch (IOException exception) {
                log.error("Unable to start external process executionId={} command={}", executionId, command, exception);
                throw new ProcessExecutionException("Unable to start external process", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("External process interrupted executionId={}", executionId);
                throw new ProcessExecutionException("External process interrupted", exception);
            } finally {
                runningProcesses.remove(executionId);
            }
        });
    }

    public boolean cancel(UUID executionId) {
        Process process = runningProcesses.get(executionId);
        if (process == null) return false;
        process.destroyForcibly();
        return true;
    }

    static List<String> adaptCommandForCurrentOs(List<String> command) {
        if (!isWindows()) return command;
        if (command.isEmpty()) return command;
        String commandLine = command.get(0) + command.subList(1, command.size()).stream()
                .map(value -> " " + quoteForWindowsCommandShell(value))
                .reduce("", String::concat);
        return List.of("cmd.exe", "/d", "/s", "/c", commandLine);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String quoteForWindowsCommandShell(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    @FunctionalInterface
    public interface LineConsumer { void accept(String line); }
}
