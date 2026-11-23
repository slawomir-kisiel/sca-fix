package pl.sca.fix.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import pl.sca.fix.core.port.PortException;

/** JSON codec for the persisted correlation result. */
public final class CorrelationJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public byte[] write(CorrelationResult result) {
        try {
            return mapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new PortException(PortException.Kind.IO,
                    "Unable to serialize correlation result", exception);
        }
    }

    public CorrelationResult read(byte[] content) {
        try {
            return mapper.readValue(content, CorrelationResult.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid correlation JSON", exception);
        }
    }
}
