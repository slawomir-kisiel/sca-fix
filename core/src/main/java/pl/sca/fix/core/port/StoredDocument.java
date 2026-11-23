package pl.sca.fix.core.port;

import java.util.Objects;

/** Opaque persisted document; serialization format is deliberately outside the port. */
public record StoredDocument(String id, byte[] content) {
    public StoredDocument {
        id = required(id);
        content = Objects.requireNonNull(content, "content must not be null").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    private static String required(String value) {
        Objects.requireNonNull(value, "id must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("id must not be blank");
        return normalized;
    }
}
