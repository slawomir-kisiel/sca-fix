package pl.sca.fix.core.plan;

import java.util.List;

public record PlanningSummary(int findings, int libraryGroups, int occurrences,
                              int plannedItems, int skippedItems, List<PlanIssue> reasons) {
    public PlanningSummary {
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }
}
