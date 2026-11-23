package pl.sca.fix.core.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.ProcessRequest;
import pl.sca.fix.core.port.ProcessResult;
import pl.sca.fix.core.port.ProcessRunnerPort;

class GitProcessAdapterTest {
    @Test
    void parsesMachineReadableStatusAndCommit() {
        RecordingRunner runner = new RecordingRunner(
                new ProcessResult(0, "## feature/sca...origin/feature/sca\n M pom.xml\n?? new.txt\n", "", Duration.ZERO, false),
                new ProcessResult(0, "abc123\n", "", Duration.ZERO, false));

        var status = new GitProcessAdapter(runner).status(Path.of("checkout"));

        assertEquals("feature/sca", status.branch());
        assertEquals("abc123", status.commitSha());
        assertEquals(List.of(Path.of("pom.xml"), Path.of("new.txt")), status.changedFiles());
        assertEquals(List.of("status", "rev-parse"), runner.commands().stream()
                .map(command -> command.command().get(1)).toList());
    }

    @Test
    void usesExplicitPathsForAddAndReturnsCommitSha() {
        RecordingRunner runner = new RecordingRunner(
                new ProcessResult(0, "", "", Duration.ZERO, false),
                new ProcessResult(0, "", "", Duration.ZERO, false),
                new ProcessResult(0, "new-sha\n", "", Duration.ZERO, false));
        GitProcessAdapter git = new GitProcessAdapter(runner);

        git.add(Path.of("checkout"), List.of(Path.of("pom.xml")));
        String sha = git.commit(Path.of("checkout"), "Apply SCA fix plan task-1");

        assertEquals("new-sha", sha);
        assertEquals(List.of(List.of("git", "add", "--", "pom.xml"),
                List.of("git", "commit", "-m", "Apply SCA fix plan task-1"),
                List.of("git", "rev-parse", "HEAD")), runner.commands().stream()
                .map(ProcessRequest::command).toList());
    }

    @Test
    void rejectsUnsafeBranchNames() {
        GitProcessAdapter git = new GitProcessAdapter(request -> {
            throw new AssertionError("Git must not be called");
        });

        assertThrows(PortException.class, () -> git.createBranch(Path.of("checkout"), "../outside"));
    }

    @Test
    void exposesProcessFailureDetails() {
        GitProcessAdapter git = new GitProcessAdapter(request ->
                new ProcessResult(128, "out", "fatal: not a git repository", Duration.ZERO, false));

        PortException exception = assertThrows(PortException.class,
                () -> git.status(Path.of("checkout")));

        assertEquals(PortException.Kind.PROCESS_FAILED, exception.kind());
        assertEquals(true, exception.getMessage().contains("fatal: not a git repository"));
    }

    @Test
    void clonesRepositoryUsingOptionalBranch() {
        RecordingRunner runner = new RecordingRunner(
                new ProcessResult(0, "Cloning...", "", Duration.ofSeconds(2), false));

        var result = new GitProcessAdapter(runner).cloneRepository(
                "https://example.test/project.git", Path.of("work/project"), "main");

        assertEquals(Path.of("work/project").toAbsolutePath().normalize(), result.destination());
        assertEquals(List.of("git", "clone", "--branch", "main", "https://example.test/project.git",
                Path.of("work/project").toAbsolutePath().normalize().toString()),
                runner.commands().get(0).command());
    }

    private static final class RecordingRunner implements ProcessRunnerPort {
        private final List<ProcessResult> results;
        private final java.util.ArrayList<ProcessRequest> commands = new java.util.ArrayList<>();
        private int index;

        private RecordingRunner(ProcessResult... results) {
            this.results = List.of(results);
        }

        @Override
        public ProcessResult run(ProcessRequest request) {
            commands.add(request);
            return results.get(Math.min(index++, results.size() - 1));
        }

        private List<ProcessRequest> commands() {
            return List.copyOf(commands);
        }
    }
}
