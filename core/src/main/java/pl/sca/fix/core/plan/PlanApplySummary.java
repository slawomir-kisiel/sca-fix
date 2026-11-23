package pl.sca.fix.core.plan;

import java.util.List;

/** Serializable summary of one apply attempt. */
public record PlanApplySummary(String planId, String status, int planned, int applied,
                               int failed, int skipped, boolean ignoreLocalChanges,
                               boolean pushBlocked, String diff,
                               List<PlanApplyItemResult> items, List<PlanIssue> errors) {
    public PlanApplySummary {
        items = List.copyOf(items == null ? List.of() : items);
        errors = List.copyOf(errors == null ? List.of() : errors);
    }
}
