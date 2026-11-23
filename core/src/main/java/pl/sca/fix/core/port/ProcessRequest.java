package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable external-process request independent of a process API. */
public record ProcessRequest(List<String> command, Path workingDirectory,
                             Map<String, String> environment, Duration timeout) {
    public ProcessRequest {
        command = List.copyOf(Objects.requireNonNull(command, "command must not be null"));
        if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("command must contain non-blank arguments");
        }
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public String displayCommand() {
        return command.stream().map(ProcessRequest::quoteIfNeeded).reduce((left, right) -> left + " " + right)
                .orElseThrow();
    }

    private static String quoteIfNeeded(String value) {
        return value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0 ? "\"" + value.replace("\"", "\\\"") + "\"" : value;
    }
}
