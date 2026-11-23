package pl.sca.fix.core.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.report.UpdateLevel;

/** Effective, non-secret configuration persisted with a workspace. */
public record PipelineConfiguration(
        int schemaVersion,
        String repository,
        String workspaceRoot,
        String pomsRoot,
        List<String> projects,
        UpdateLevel updateLevel,
        boolean overrideReview,
        boolean reportAvailable,
        boolean apply,
        boolean commit,
        boolean push,
        boolean ignoreLocalChanges,
        String proxyHost,
        Integer proxyPort,
        String proxyUsername,
        String proxyPassword,
        List<String> disabledStrategies,
        List<String> skipLibraries,
        UpdateLevel cveOverrideUpdateLevel) {

    @JsonCreator
    public PipelineConfiguration(@JsonProperty("schemaVersion") int schemaVersion,
                                 @JsonProperty("repository") String repository,
                                 @JsonProperty("workspaceRoot") String workspaceRoot,
                                 @JsonProperty("pomsRoot") String pomsRoot,
                                 @JsonProperty("projects") List<String> projects,
                                 @JsonProperty("updateLevel") UpdateLevel updateLevel,
                                 @JsonProperty("overrideReview") boolean overrideReview,
                                 @JsonProperty("reportAvailable") boolean reportAvailable,
                                 @JsonProperty("apply") boolean apply,
                                 @JsonProperty("commit") boolean commit,
                                 @JsonProperty("push") boolean push,
                                 @JsonProperty("ignoreLocalChanges") boolean ignoreLocalChanges,
                                 @JsonProperty("proxyHost") String proxyHost,
                                 @JsonProperty("proxyPort") Integer proxyPort,
                                 @JsonProperty("proxyUsername") String proxyUsername,
                                 @JsonProperty("proxyPassword") String proxyPassword,
                                 @JsonProperty("disabledStrategies") List<String> disabledStrategies,
                                 @JsonProperty("skipLibraries") List<String> skipLibraries,
                                 @JsonProperty("cveOverrideUpdateLevel") UpdateLevel cveOverrideUpdateLevel) {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported configuration schemaVersion: " + schemaVersion);
        this.schemaVersion = schemaVersion;
        this.repository = required(repository, "repository");
        this.workspaceRoot = required(workspaceRoot, "workspaceRoot");
        this.pomsRoot = required(pomsRoot, "pomsRoot");
        this.projects = List.copyOf(projects == null ? List.of() : projects);
        this.updateLevel = updateLevel == null ? UpdateLevel.MINOR : updateLevel;
        this.overrideReview = overrideReview;
        this.reportAvailable = reportAvailable;
        this.apply = apply;
        this.commit = commit;
        this.push = push;
        this.ignoreLocalChanges = ignoreLocalChanges;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
        this.disabledStrategies = List.copyOf(disabledStrategies == null ? List.of() : disabledStrategies);
        this.skipLibraries = List.copyOf(skipLibraries == null ? List.of() : skipLibraries);
        this.cveOverrideUpdateLevel = cveOverrideUpdateLevel == null ? UpdateLevel.PATCH : cveOverrideUpdateLevel;
    }

    public PipelineConfiguration(int schemaVersion, String repository, String workspaceRoot, String pomsRoot,
                                 List<String> projects, UpdateLevel updateLevel, boolean overrideReview,
                                 boolean reportAvailable, boolean apply, boolean commit, boolean push,
                                 boolean ignoreLocalChanges) {
        this(schemaVersion, repository, workspaceRoot, pomsRoot, projects, updateLevel, overrideReview,
                reportAvailable, apply, commit, push, ignoreLocalChanges, null, null, null, null, List.of(), List.of(), UpdateLevel.PATCH);
    }

    public static PipelineConfiguration from(PipelineRequest request) {
        return new PipelineConfiguration(1, request.repository().toString(), request.workspaceRoot().toString(),
                request.pomsRoot().toString(), request.projects(), request.updateLevel(), request.overrideReview(),
                request.reportAvailable(), request.apply(), request.commit(), request.push(), request.ignoreLocalChanges(),
                request.proxyHost(), request.proxyPort(), request.proxyUsername(), request.proxyPassword(), request.disabledStrategies(), request.skipLibraries(), request.cveOverrideUpdateLevel());
    }

    public PipelineRequest toRequest(String workspaceId, PipelineMode mode) {
        return new PipelineRequest(workspaceId, Path.of(repository), Path.of(workspaceRoot), Path.of(pomsRoot),
                projects, updateLevel, mode, overrideReview, reportAvailable, apply, commit, push, ignoreLocalChanges,
                proxyHost, proxyPort, proxyUsername, proxyPassword, disabledStrategies, skipLibraries, cveOverrideUpdateLevel);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
