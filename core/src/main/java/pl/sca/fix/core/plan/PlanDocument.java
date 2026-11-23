package pl.sca.fix.core.plan;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.report.UpdateLevel;

/** Portable plan document shared by Web, CLI and Maven Plugin. */
public record PlanDocument(int schemaVersion, String planId, Instant createdAt,
                           PlanRepository repository, UpdateLevel updateLevel,
                           List<PlanItem> items, List<PlanIssue> warnings, List<PlanIssue> errors,
                           String status) {
    public PlanDocument {
        items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
        errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
        status = status == null || status.isBlank() ? null : status;
    }

    /** Compatibility constructor for plans created before execution status existed. */
    public PlanDocument(int schemaVersion, String planId, Instant createdAt,
                        PlanRepository repository, UpdateLevel updateLevel,
                        List<PlanItem> items, List<PlanIssue> warnings, List<PlanIssue> errors) {
        this(schemaVersion, planId, createdAt, repository, updateLevel, items, warnings, errors, null);
    }
}
