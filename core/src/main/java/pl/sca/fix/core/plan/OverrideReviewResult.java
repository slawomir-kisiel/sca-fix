package pl.sca.fix.core.plan;

import java.util.List;

public record OverrideReviewResult(List<OverrideReviewEntry> entries, PlanDocument plan) {
    public OverrideReviewResult {
        entries = List.copyOf(entries == null ? List.of() : entries);
    }
}
