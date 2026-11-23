package pl.sca.fix.core.port;

import java.time.Duration;
import java.util.Objects;

/** Materialized result of an external process execution. */
public record ProcessResult(int exitCode, String stdout, String stderr,
                            Duration duration, boolean timedOut) {
    public ProcessResult {
        stdout = Objects.requireNonNullElse(stdout, "");
        stderr = Objects.requireNonNullElse(stderr, "");
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
    }

    public boolean successful() {
        return !timedOut && exitCode == 0;
    }
}
