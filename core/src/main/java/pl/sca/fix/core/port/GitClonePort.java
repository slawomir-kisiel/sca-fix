package pl.sca.fix.core.port;

import java.nio.file.Path;

/** Port for obtaining a local checkout from a Git repository. */
public interface GitClonePort {
    GitCloneResult cloneRepository(String repositoryUrl, Path destination, String branch);
}

