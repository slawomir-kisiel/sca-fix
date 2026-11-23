package pl.sca.fix.core.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import pl.sca.fix.core.port.PortException;

public final class VersionRecommendationJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public byte[] write(VersionRecommendation value) {
        try { return mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8); }
        catch (Exception exception) { throw new PortException(PortException.Kind.IO, "Unable to serialize version recommendation", exception); }
    }

    public VersionRecommendation read(byte[] content) {
        try { return mapper.readValue(content, VersionRecommendation.class); }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid version recommendation JSON", exception); }
    }
}
