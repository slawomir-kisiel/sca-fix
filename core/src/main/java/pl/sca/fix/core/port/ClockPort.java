package pl.sca.fix.core.port;

import java.time.Instant;

/** Time source used by core services and deterministic tests.
 * Implementations should report an unavailable time source as {@link PortException}. */
public interface ClockPort {
    Instant now();
}
