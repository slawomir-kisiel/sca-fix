package pl.sca.fix.core.plan;

import java.util.List;

/** Prepared POM change. It contains no filesystem side effects. */
public record PomChange(String pomPath, String strategy, boolean changed,
                        String originalSha256, String changedSha256,
                        String indentation, String originalContent, String changedContent,
                        String diff, List<PlanIssue> warnings, List<PlanIssue> errors) {
    public PomChange {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        errors = List.copyOf(errors == null ? List.of() : errors);
    }
}
