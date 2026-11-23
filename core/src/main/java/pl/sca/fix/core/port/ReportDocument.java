package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.util.Objects;

/** Materialized report input passed to a format adapter. */
public record ReportDocument(byte[] content, Path sourceFile, String formatHint) {
    public ReportDocument {
        content = Objects.requireNonNull(content, "content must not be null").clone();
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        formatHint = formatHint == null || formatHint.isBlank() ? null : formatHint.trim();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
