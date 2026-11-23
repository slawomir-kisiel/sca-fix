package pl.sca.fix.core.workspace;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.time.Instant;

/** Persisted, backend-independent state of one CLI workspace. */
public record WorkspaceState(
        int schemaVersion,
        String workspaceId,
        Instant createdAt,
        Instant updatedAt,
        Status status,
        Stage currentStage,
        Configuration configuration,
        List<Repository> repositories,
        List<Project> projects,
        Artifacts artifacts,
        List<StageState> stages,
        List<Issue> warnings,
        List<Issue> errors) {

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public WorkspaceState {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        workspaceId = required(workspaceId, "workspaceId");
        if (!ID_PATTERN.matcher(workspaceId).matches()) {
            throw new IllegalArgumentException("workspaceId contains unsupported characters");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(currentStage, "currentStage must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        repositories = copy(repositories, "repositories");
        projects = copy(projects, "projects");
        Objects.requireNonNull(artifacts, "artifacts must not be null");
        stages = copy(stages, "stages");
        warnings = copy(warnings, "warnings");
        errors = copy(errors, "errors");
    }

    public enum Status { NEW, RUNNING, PAUSED, COMPLETED, FAILED }

    public enum Stage { DISCOVERY, MAVEN_ANALYSIS, REPORT, CORRELATION, OVERRIDE_REVIEW, PLANNING, APPLY, PUBLISH, SUMMARY }

    public enum StageStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

    public record Configuration(String repository, String poms, List<String> projects,
                                boolean ignoreLocalChanges, String updateLevel) {
        public Configuration {
            repository = required(repository, "configuration.repository");
            poms = required(poms, "configuration.poms");
            projects = copy(projects, "configuration.projects");
            updateLevel = updateLevel == null || updateLevel.isBlank() ? "MINOR" : updateLevel.trim();
        }
    }

    public record Repository(String rootDirectory, String remoteUrl, String baseCommitSha) {
        public Repository {
            rootDirectory = required(rootDirectory, "repository.rootDirectory");
            remoteUrl = blankToNull(remoteUrl);
            baseCommitSha = blankToNull(baseCommitSha);
        }
    }

    public record Project(String groupId, String artifactId, String version,
                          String pomPath, boolean editable) {
        public Project {
            groupId = required(groupId, "project.groupId");
            artifactId = required(artifactId, "project.artifactId");
            version = required(version, "project.version");
            pomPath = required(pomPath, "project.pomPath");
        }
    }

    public record Artifacts(String report, String plan, List<PomArtifact> poms) {
        public Artifacts {
            report = blankToNull(report);
            plan = blankToNull(plan);
            poms = copy(poms, "artifacts.poms");
        }
    }

    public record PomArtifact(String key, String effectivePom, String dependencyTree) {
        public PomArtifact {
            key = required(key, "artifact.key");
            effectivePom = blankToNull(effectivePom);
            dependencyTree = blankToNull(dependencyTree);
        }
    }

    public record StageState(String name, StageStatus status, Instant startedAt,
                             Instant finishedAt, int attempt, List<String> artifactPaths,
                             String errorCode, String errorMessage) {
        public StageState {
            name = required(name, "stage.name");
            Objects.requireNonNull(status, "stage.status must not be null");
            if (attempt < 0) throw new IllegalArgumentException("stage.attempt must not be negative");
            artifactPaths = copy(artifactPaths, "stage.artifactPaths");
            errorCode = blankToNull(errorCode);
            errorMessage = blankToNull(errorMessage);
        }
    }

    public record Issue(String reason, String message, String stage, Instant occurredAt) {
        public Issue {
            reason = required(reason, "issue.reason");
            message = required(message, "issue.message");
            stage = blankToNull(stage);
        }
    }

    public static boolean isValidId(String value) {
        return value != null && ID_PATTERN.matcher(value).matches();
    }

    private static <T> List<T> copy(List<T> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " must not be null"));
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
