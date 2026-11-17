package pl.sca.fix.integration.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GitCheckoutInspector {
    public GitCheckoutInfo inspect(Path checkout) {
        if (checkout == null || !Files.isDirectory(checkout)) {
            throw new GitCheckoutException("CHECKOUT_PATH_INVALID", "Checkout directory does not exist: " + checkout);
        }
        String workTree = execute(checkout, List.of("rev-parse", "--is-inside-work-tree"), false);
        if (!"true".equalsIgnoreCase(workTree)) {
            throw new GitCheckoutException("NOT_A_GIT_CHECKOUT", "Directory is not a Git checkout: " + checkout);
        }
        String remote = execute(checkout, List.of("remote", "get-url", "origin"), false);
        String branch = execute(checkout, List.of("symbolic-ref", "--short", "-q", "HEAD"), false);
        String commit = execute(checkout, List.of("rev-parse", "HEAD"), true);
        return new GitCheckoutInfo(nullIfBlank(remote), nullIfBlank(branch), commit);
    }

    public boolean isTracked(Path checkout, Path file) {
        if (checkout == null || file == null || !file.normalize().startsWith(checkout.normalize())) {
            throw new GitCheckoutException("POM_PATH_INVALID", "File is outside the Git checkout: " + file);
        }
        String relative = checkout.normalize().relativize(file.normalize()).toString().replace('\\', '/');
        try {
            Process process = new ProcessBuilder("git", "-C", checkout.toString(), "ls-files",
                    "--error-unmatch", "--", relative).redirectErrorStream(true).start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            int exitCode = process.waitFor();
            if (exitCode == 0) return true;
            if (exitCode == 1) return false;
            throw new GitCheckoutException("GIT_COMMAND_FAILED", "Git command failed: git ls-files --error-unmatch -- " + relative);
        } catch (IOException exception) {
            throw new GitCheckoutException("GIT_UNAVAILABLE", "Unable to execute Git", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitCheckoutException("GIT_INTERRUPTED", "Git inspection was interrupted", exception);
        }
    }

    private String execute(Path checkout, List<String> arguments, boolean required) {
        try {
            List<String> command = new java.util.ArrayList<>();
            command.add("git");
            command.add("-C");
            command.add(checkout.toString());
            command.addAll(arguments);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (required) {
                    throw new GitCheckoutException("GIT_COMMAND_FAILED", "Git command failed: " + String.join(" ", arguments));
                }
                return "";
            }
            return output;
        } catch (IOException exception) {
            throw new GitCheckoutException("GIT_UNAVAILABLE", "Unable to execute Git", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitCheckoutException("GIT_INTERRUPTED", "Git inspection was interrupted", exception);
        }
    }

    private String nullIfBlank(String value) { return value == null || value.isBlank() ? null : value; }
}
