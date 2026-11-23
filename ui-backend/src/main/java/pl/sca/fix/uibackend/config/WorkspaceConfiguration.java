package pl.sca.fix.uibackend.config;

import java.util.List;

public record WorkspaceConfiguration(
        int schemaVersion,
        List<String> analysisProjects,
        List<String> editableProjects,
        String updateLevel,
        String cveOverrideUpdateLevel,
        String ambiguousStrategy,
        List<String> skipLibraries,
        List<String> disabledStrategies,
        String proxyHost,
        Integer proxyPort,
        List<String> mavenRepositories,
        boolean ignoreLocalChanges) {
    public WorkspaceConfiguration {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        analysisProjects = List.copyOf(analysisProjects == null ? List.of("*") : analysisProjects);
        editableProjects = List.copyOf(editableProjects == null ? List.of("*") : editableProjects);
        updateLevel = value(updateLevel, "MINOR");
        cveOverrideUpdateLevel = value(cveOverrideUpdateLevel, "PATCH");
        ambiguousStrategy = value(ambiguousStrategy, "SHALLOWEST");
        skipLibraries = List.copyOf(skipLibraries == null ? List.of() : skipLibraries);
        disabledStrategies = List.copyOf(disabledStrategies == null ? List.of() : disabledStrategies);
        mavenRepositories = List.copyOf(mavenRepositories == null ? List.of() : mavenRepositories);
        if (proxyPort != null && (proxyPort < 1 || proxyPort > 65535)) {
            throw new IllegalArgumentException("proxyPort must be between 1 and 65535");
        }
    }

    public static WorkspaceConfiguration defaults() {
        return new WorkspaceConfiguration(1, List.of("*"), List.of("*"), "MINOR", "PATCH",
                "SHALLOWEST", List.of(), List.of(), null, null, List.of(), false);
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

