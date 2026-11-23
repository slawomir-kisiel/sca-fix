package pl.sca.fix.core.report;

import java.util.List;

public record CorrelationResult(int schemaVersion, List<CorrelationEntry> entries,
                                List<String> warnings, List<String> errors) {
    public CorrelationResult {
        entries = List.copyOf(entries == null ? List.of() : entries);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        errors = List.copyOf(errors == null ? List.of() : errors);
    }
}
