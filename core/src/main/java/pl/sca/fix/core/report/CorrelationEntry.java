package pl.sca.fix.core.report;

import java.util.List;

public record CorrelationEntry(String libraryKey, CorrelationStatus status,
                               List<DependencyTreeOccurrence> occurrences,
                               List<String> warnings) {
    public CorrelationEntry {
        occurrences = List.copyOf(occurrences == null ? List.of() : occurrences);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
