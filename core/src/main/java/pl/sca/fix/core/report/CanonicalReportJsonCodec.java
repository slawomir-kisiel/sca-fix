package pl.sca.fix.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import pl.sca.fix.core.port.PortException;

public final class CanonicalReportJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public byte[] write(CanonicalScanReport report) {
        try { return mapper.writeValueAsString(report).getBytes(StandardCharsets.UTF_8); }
        catch (Exception exception) { throw new PortException(PortException.Kind.IO, "Unable to serialize canonical report", exception); }
    }

    public CanonicalScanReport read(byte[] content) {
        try {
            CanonicalScanReport result = mapper.readValue(content, CanonicalScanReport.class);
            if (result.schemaVersion() != 1) throw new IllegalArgumentException("Unsupported report schema version");
            return result;
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid canonical report JSON", exception); }
    }
}
