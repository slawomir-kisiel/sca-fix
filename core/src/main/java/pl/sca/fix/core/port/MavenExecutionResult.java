package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.util.Objects;

/** Maven operation result with the generated artifact location. */
public record MavenExecutionResult(ProcessResult processResult, Path outputFile) {
    public MavenExecutionResult {
        Objects.requireNonNull(processResult, "processResult must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");
    }

    public boolean successful() {
        return processResult.successful();
    }
}
