package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.util.Objects;

/** Identifies an input report without prescribing its format. */
public record ReportSourceRequest(Path inputFile, String formatHint) {
    public ReportSourceRequest {
        Objects.requireNonNull(inputFile, "inputFile must not be null");
        formatHint = formatHint == null || formatHint.isBlank() ? null : formatHint.trim();
    }
}
