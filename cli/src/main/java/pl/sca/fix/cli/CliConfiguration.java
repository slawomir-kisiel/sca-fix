package pl.sca.fix.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.report.UpdateLevel;

/** Immutable, validated CLI input. It contains no backend or Spring types. */
public record CliConfiguration(
        CliCommand command,
        ScanMode scanMode,
        String workspaceId,
        String repository,
        List<String> projects,
        String reportSource,
        String reportInput,
        String reportOutput,
        String plan,
        UpdateLevel updateLevel,
        String ambiguousStrategy,
        String versionInfoTtl,
        boolean ignoreLocalChanges,
        boolean commit,
        String pushBranch,
        boolean overrideReview,
        String configFile,
        Path workspaceRoot,
        Path pomsRoot,
        boolean verbose,
        String proxyHost,
        Integer proxyPort,
        String proxyUsername,
        String proxyPassword,
        boolean apply,
        List<String> disabledStrategies,
        List<String> skipLibraries,
        UpdateLevel cveOverrideUpdateLevel) {
    public CliConfiguration(CliCommand command, ScanMode scanMode, String workspaceId, String repository,
                            List<String> projects, String reportSource, String reportInput, String reportOutput,
                            String plan, UpdateLevel updateLevel, String ambiguousStrategy, String versionInfoTtl,
                            boolean ignoreLocalChanges, boolean commit, String pushBranch, boolean overrideReview,
                            String configFile, Path workspaceRoot, Path pomsRoot, boolean verbose) {
        this(command, scanMode, workspaceId, repository, projects, reportSource, reportInput, reportOutput, plan,
                updateLevel, ambiguousStrategy, versionInfoTtl, ignoreLocalChanges, commit, pushBranch,
                overrideReview, configFile, workspaceRoot, pomsRoot, verbose, null, null, null, null, false, List.of(), List.of(), UpdateLevel.PATCH);
    }

    public CliConfiguration {
        command = Objects.requireNonNull(command);
        scanMode = scanMode == null ? ScanMode.NEW : scanMode;
        projects = List.copyOf(projects == null ? List.of("*") : projects);
        updateLevel = updateLevel == null ? UpdateLevel.MINOR : updateLevel;
        ambiguousStrategy = ambiguousStrategy == null ? "COMMON_SOURCE" : ambiguousStrategy;
        versionInfoTtl = versionInfoTtl == null ? "R1d,S10m" : versionInfoTtl;
        workspaceRoot = workspaceRoot == null ? Path.of("target", "scafix", "workspaces") : workspaceRoot;
        pomsRoot = pomsRoot == null ? Path.of("target", "scafix", "poms") : pomsRoot;
        disabledStrategies = disabledStrategies == null ? List.of() : disabledStrategies.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).map(String::toUpperCase).distinct().toList();
        skipLibraries = skipLibraries == null ? List.of() : skipLibraries.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        cveOverrideUpdateLevel = cveOverrideUpdateLevel == null ? UpdateLevel.PATCH : cveOverrideUpdateLevel;
    }
}
