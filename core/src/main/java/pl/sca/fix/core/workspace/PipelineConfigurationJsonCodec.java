package pl.sca.fix.core.workspace;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import pl.sca.fix.core.pipeline.PipelineConfiguration;

/** Deterministic UTF-8 JSON codec for the workspace configuration snapshot. */
public final class PipelineConfigurationJsonCodec {
    private final ObjectMapper mapper;

    public PipelineConfigurationJsonCodec() {
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public byte[] write(PipelineConfiguration configuration) {
        try { return mapper.writeValueAsBytes(configuration); }
        catch (Exception exception) { throw new WorkspaceStateException("Unable to serialize workspace configuration", exception); }
    }

    public PipelineConfiguration read(byte[] content, String sourceDescription) {
        try { return mapper.readValue(content, PipelineConfiguration.class); }
        catch (Exception exception) { throw new WorkspaceStateException("Unable to read workspace configuration from " + sourceDescription, exception); }
    }

    public String writeText(PipelineConfiguration configuration) {
        return new String(write(configuration), StandardCharsets.UTF_8);
    }
}
