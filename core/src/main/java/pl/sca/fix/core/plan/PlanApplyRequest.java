package pl.sca.fix.core.plan;

import java.nio.file.Path;
import java.util.List;

/** Execution options for one plan application attempt. */
public record PlanApplyRequest(PlanDocument plan, Path repository,
                               boolean ignoreLocalChanges, boolean existingExecution,
                               boolean dryRun, List<String> disabledStrategies,
                               List<String> skipLibraries) {
    public PlanApplyRequest(PlanDocument plan, Path repository, boolean ignoreLocalChanges,
                            boolean existingExecution, boolean dryRun) {
        this(plan, repository, ignoreLocalChanges, existingExecution, dryRun, List.of(), List.of());
    }
    public PlanApplyRequest(PlanDocument plan, Path repository, boolean ignoreLocalChanges,
                            boolean existingExecution, boolean dryRun, List<String> disabledStrategies) {
        this(plan, repository, ignoreLocalChanges, existingExecution, dryRun, disabledStrategies, List.of());
    }
    public PlanApplyRequest {
        if (plan == null) throw new IllegalArgumentException("plan must not be null");
        if (repository == null) throw new IllegalArgumentException("repository must not be null");
        disabledStrategies = disabledStrategies == null ? List.of() : disabledStrategies.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).map(String::toUpperCase).distinct().toList();
        skipLibraries = skipLibraries == null ? List.of() : skipLibraries.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        PlanSkipRule.parseAll(skipLibraries);
    }
}
