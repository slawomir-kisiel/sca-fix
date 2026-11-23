package pl.sca.fix.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorrelationJsonCodecTest {
    @Test
    void roundTripsCorrelationResult() {
        CorrelationResult source = new CorrelationResult(1,
                List.of(new CorrelationEntry("g:a:1.2.3", CorrelationStatus.ORPHAN,
                        List.of(), List.of("not found"))),
                List.of("warning"), List.of());

        CorrelationResult restored = new CorrelationJsonCodec().read(
                new CorrelationJsonCodec().write(source));

        assertEquals(source, restored);
    }
}
