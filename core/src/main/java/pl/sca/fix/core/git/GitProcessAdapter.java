package pl.sca.fix.core.git;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitBranch;
import pl.sca.fix.core.port.GitStatus;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.ProcessRequest;
import pl.sca.fix.core.port.ProcessResult;
import pl.sca.fix.core.port.ProcessRunnerPort;

/** Git adapter based on the platform's Git executable. */
public final class GitProcessAdapter implements GitPort, pl.sca.fix.core.port.GitClonePort {
    private static final Logger LOG = LoggerFactory.getLogger(GitProcessAdapter.class);
    private final ProcessRunnerPort processes;
    private final String executable;
    private final Duration timeout;

    public GitProcessAdapter(ProcessRunnerPort processes) {
        this(processes, "git", Duration.ofMinutes(2));
    }

    public GitProcessAdapter(ProcessRunnerPort processes, String executable, Duration timeout) {
        this.processes = Objects.requireNonNull(processes, "processes must not be null");
        this.executable = executable == null || executable.isBlank() ? "git" : executable;
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
    }

    @Override
    public pl.sca.fix.core.port.GitCloneResult cloneRepository(String repositoryUrl, Path destination, String branch) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new PortException(PortException.Kind.INVALID_INPUT, "Repository URL must not be blank");
        }
        if (destination == null) {
            throw new PortException(PortException.Kind.INVALID_INPUT, "Clone destination must not be null");
        }
        Path target = destination.toAbsolutePath().normalize();
        List<String> arguments = new ArrayList<>(List.of("clone"));
        if (branch != null && !branch.isBlank()) {
            validateBranch(branch);
            arguments.addAll(List.of("--branch", branch.trim()));
        }
        arguments.addAll(List.of(repositoryUrl.trim(), target.toString()));
        ProcessResult result = runWithoutRepository(arguments.toArray(String[]::new));
        return new pl.sca.fix.core.port.GitCloneResult(target, branch, null, result.duration());
    }

    @Override
    public GitStatus status(Path repository) {
        ProcessResult result = run(repository, "status", "--porcelain=v1", "--branch");
        String branch = null;
        List<Path> changed = new ArrayList<>();
        for (String line : result.stdout().split("\\R", -1)) {
            if (line.startsWith("## ")) {
                String branchValue = line.substring(3).trim();
                int remoteSeparator = branchValue.indexOf("...");
                branch = remoteSeparator < 0 ? branchValue : branchValue.substring(0, remoteSeparator);
                if (branch.startsWith("HEAD ")) branch = null;
                continue;
            }
            if (line.length() < 3 || line.isBlank()) continue;
            String file = line.substring(3).trim();
            int renameSeparator = file.indexOf(" -> ");
            if (renameSeparator >= 0) file = file.substring(renameSeparator + 4).trim();
            file = unquoteGitPath(file);
            if (!file.isBlank()) changed.add(Path.of(file));
        }
        return new GitStatus(true, changed.isEmpty(), currentCommitSha(repository), changed, branch);
    }

    @Override
    public String currentCommitSha(Path repository) {
        return run(repository, "rev-parse", "HEAD").stdout().trim();
    }

    @Override
    public List<Path> trackedFiles(Path repository) {
        String output = run(repository, "ls-files", "-z").stdout();
        if (output.isEmpty()) return List.of();
        return Arrays.stream(output.split("\u0000", -1)).filter(value -> !value.isBlank()).map(Path::of).toList();
    }

    @Override
    public void add(Path repository, List<Path> paths) {
        if (paths == null || paths.isEmpty()) throw new PortException(PortException.Kind.INVALID_INPUT,
                "At least one path is required for git add");
        List<String> command = new ArrayList<>(List.of("add", "--"));
        paths.forEach(path -> command.add(path.toString()));
        run(repository, command.toArray(String[]::new));
    }

    @Override
    public GitBranch createBranch(Path repository, String branchName) {
        validateBranch(branchName);
        run(repository, "checkout", "-b", branchName);
        return new GitBranch(branchName, currentCommitSha(repository));
    }

    @Override
    public GitBranch checkoutBranch(Path repository, String branchName) {
        validateBranch(branchName);
        run(repository, "checkout", branchName);
        return new GitBranch(branchName, currentCommitSha(repository));
    }

    @Override
    public void deleteBranch(Path repository, String branchName) {
        validateBranch(branchName);
        run(repository, "branch", "-D", branchName);
    }

    @Override
    public String commit(Path repository, String message) {
        if (message == null || message.isBlank()) throw new PortException(PortException.Kind.INVALID_INPUT,
                "Commit message must not be blank");
        run(repository, "commit", "-m", message);
        return currentCommitSha(repository);
    }

    @Override
    public void push(Path repository, String branchName) {
        validateBranch(branchName);
        run(repository, "push", "origin", branchName);
    }

    private ProcessResult run(Path repository, String... arguments) {
        if (repository == null) throw new PortException(PortException.Kind.INVALID_INPUT, "Repository must not be null");
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(arguments));
        ProcessRequest request = new ProcessRequest(command, repository, null, timeout);
        LOG.info("Executing Git command repository={} command={}", repository, request.displayCommand());
        ProcessResult result = processes.run(request);
        if (!result.successful()) {
            PortException.Kind kind = result.timedOut() ? PortException.Kind.TIMEOUT : PortException.Kind.PROCESS_FAILED;
            throw new PortException(kind, "Git command failed exitCode=" + result.exitCode()
                    + " command=" + request.displayCommand() + " stdout=" + compact(result.stdout())
                    + " stderr=" + compact(result.stderr()));
        }
        return result;
    }

    private ProcessResult runWithoutRepository(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(arguments));
        ProcessRequest request = new ProcessRequest(command, Path.of(".").toAbsolutePath().normalize(), null, timeout);
        LOG.info("Executing Git clone command command={}", request.displayCommand());
        ProcessResult result = processes.run(request);
        if (!result.successful()) {
            PortException.Kind kind = result.timedOut() ? PortException.Kind.TIMEOUT : PortException.Kind.PROCESS_FAILED;
            throw new PortException(kind, "Git clone failed exitCode=" + result.exitCode()
                    + " command=" + request.displayCommand() + " stderr=" + compact(result.stderr()));
        }
        return result;
    }

    private static void validateBranch(String branchName) {
        if (branchName == null || branchName.isBlank() || branchName.startsWith("-")
                || branchName.contains("..") || branchName.contains(" ") || branchName.contains("~")
                || branchName.contains("^") || branchName.contains(":") || branchName.contains("\\"))
            throw new PortException(PortException.Kind.INVALID_INPUT, "Invalid Git branch name");
    }

    private static String compact(String value) {
        String normalized = Objects.requireNonNullElse(value, "").replaceAll("\\s+", " ").trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    private static String unquoteGitPath(String value) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') return value;
        StringBuilder result = new StringBuilder(value.length() - 2);
        for (int index = 1; index < value.length() - 1; index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length() - 1) {
                result.append(current);
                continue;
            }
            char escaped = value.charAt(++index);
            switch (escaped) {
                case '"', '\\' -> result.append(escaped);
                case 'a' -> result.append('\u0007');
                case 'b' -> result.append('\b');
                case 't' -> result.append('\t');
                case 'n' -> result.append('\n');
                case 'v' -> result.append('\u000b');
                case 'f' -> result.append('\f');
                case 'r' -> result.append('\r');
                default -> result.append(escaped);
            }
        }
        return result.toString();
    }
}
