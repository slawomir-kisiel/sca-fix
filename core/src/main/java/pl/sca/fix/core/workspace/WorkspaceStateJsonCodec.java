package pl.sca.fix.core.workspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;

/** Deterministic UTF-8 JSON codec for workspace state. */
public final class WorkspaceStateJsonCodec {
    private final ObjectMapper mapper;

    public WorkspaceStateJsonCodec() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public byte[] write(WorkspaceState state) {
        try {
            return mapper.writeValueAsBytes(state);
        } catch (JsonProcessingException exception) {
            throw new WorkspaceStateException("Unable to serialize workspace " + state.workspaceId(), exception);
        }
    }

    public WorkspaceState read(byte[] content, String sourceDescription) {
        if (content == null) throw new WorkspaceStateException("Workspace state content must not be null");
        try {
            JsonNode document = mapper.readTree(content);
            migrateLegacyWarnings(document);
            WorkspaceState state = mapper.treeToValue(document, WorkspaceState.class);
            if (state.schemaVersion() != 1) {
                throw new WorkspaceStateException("Unsupported workspace schemaVersion: " + state.schemaVersion());
            }
            return state;
        } catch (WorkspaceStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WorkspaceStateException("Unable to read workspace state from " + sourceDescription, exception);
        }
    }

    public String writeText(WorkspaceState state) {
        return new String(write(state), StandardCharsets.UTF_8);
    }

    private static void migrateLegacyWarnings(JsonNode document) {
        if (!(document instanceof ObjectNode root) || !(root.get("warnings") instanceof ArrayNode warnings)) return;
        for (int index = 0; index < warnings.size(); index++) {
            JsonNode warning = warnings.get(index);
            if (!warning.isTextual()) continue;
            String value = warning.asText();
            int separator = value.indexOf(':');
            ObjectNode replacement = JsonNodeFactory.instance.objectNode();
            replacement.put("reason", separator > 0 ? value.substring(0, separator) : "LEGACY_WARNING");
            replacement.put("message", separator > 0 ? value.substring(separator + 1) : value);
            replacement.putNull("stage");
            replacement.putNull("occurredAt");
            warnings.set(index, replacement);
        }
    }
}
