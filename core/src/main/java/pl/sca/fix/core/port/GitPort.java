package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.util.List;

/** Git operations required by repository analysis and publication workflows.
 * Invalid repositories and failed Git commands are reported as {@link PortException}. */
public interface GitPort {
    GitStatus status(Path repository);

    default String currentCommitSha(Path repository) {
        return status(repository).commitSha();
    }

    List<Path> trackedFiles(Path repository);

    void add(Path repository, List<Path> paths);

    GitBranch createBranch(Path repository, String branchName);

    GitBranch checkoutBranch(Path repository, String branchName);

    void deleteBranch(Path repository, String branchName);

    String commit(Path repository, String message);

    void push(Path repository, String branchName);
}
