package pl.sca.fix.core.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.report.UpdateLevel;

public record PipelineRequest(String workspaceId, Path repository, Path workspaceRoot, Path pomsRoot,
                              List<String> projects, UpdateLevel updateLevel, PipelineMode mode,
                              boolean overrideReview, boolean reportAvailable, boolean apply,
                              boolean commit, boolean push, boolean ignoreLocalChanges,
                              String proxyHost, Integer proxyPort, String proxyUsername, String proxyPassword,
                              List<String> disabledStrategies, List<String> skipLibraries,
                              UpdateLevel cveOverrideUpdateLevel) {
    public PipelineRequest(String workspaceId, Path repository, Path workspaceRoot, Path pomsRoot,
                           List<String> projects, UpdateLevel updateLevel, PipelineMode mode,
                           boolean overrideReview, boolean reportAvailable, boolean apply,
                           boolean commit, boolean push, boolean ignoreLocalChanges) {
        this(workspaceId, repository, workspaceRoot, pomsRoot, projects, updateLevel, mode,
                overrideReview, reportAvailable, apply, commit, push, ignoreLocalChanges, null, null, null, null, List.of(), List.of(), UpdateLevel.PATCH);
    }
    public PipelineRequest(String workspaceId, Path repository, Path workspaceRoot, Path pomsRoot,
                           List<String> projects, UpdateLevel updateLevel, PipelineMode mode,
                           boolean overrideReview, boolean reportAvailable, boolean apply,
                           boolean commit, boolean push, boolean ignoreLocalChanges,
                           String proxyHost, Integer proxyPort, String proxyUsername, String proxyPassword,
                           List<String> disabledStrategies, List<String> skipLibraries) {
        this(workspaceId, repository, workspaceRoot, pomsRoot, projects, updateLevel, mode,
                overrideReview, reportAvailable, apply, commit, push, ignoreLocalChanges,
                proxyHost, proxyPort, proxyUsername, proxyPassword, disabledStrategies, skipLibraries, UpdateLevel.PATCH);
    }
    public PipelineRequest {
        if (mode == PipelineMode.CONTINUE && repository == null && workspaceRoot == null && pomsRoot == null) {
            // The effective values are loaded from the workspace config snapshot by the pipeline.
        } else {
            repository = Objects.requireNonNull(repository, "repository must not be null").toAbsolutePath().normalize();
            workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null").toAbsolutePath().normalize();
            pomsRoot = Objects.requireNonNull(pomsRoot, "pomsRoot must not be null").toAbsolutePath().normalize();
        }
        projects = List.copyOf(projects == null ? List.of() : projects);
        disabledStrategies = disabledStrategies == null ? List.of() : disabledStrategies.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).map(String::toUpperCase).distinct().toList();
        skipLibraries = skipLibraries == null ? List.of() : skipLibraries.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        cveOverrideUpdateLevel = cveOverrideUpdateLevel == null ? UpdateLevel.PATCH : cveOverrideUpdateLevel;
        updateLevel = updateLevel == null ? UpdateLevel.MINOR : updateLevel;
        mode = mode == null ? PipelineMode.NEW : mode;
        if (mode != PipelineMode.NEW && (workspaceId == null || workspaceId.isBlank()))
            throw new IllegalArgumentException("workspaceId is required for continue/restart");
        if (mode != PipelineMode.CONTINUE && !reportAvailable && !overrideReview && !apply)
            throw new IllegalArgumentException("Report is required unless overrideReview is enabled");
        if (push && !commit) throw new IllegalArgumentException("push requires commit");
        if (push && ignoreLocalChanges) throw new IllegalArgumentException("push is blocked with ignoreLocalChanges");
    }
}
