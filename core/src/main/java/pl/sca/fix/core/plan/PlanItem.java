package pl.sca.fix.core.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PlanItem(String itemId, PlanProject project, PlanArtifact artifact,
                       String currentVersion, String targetVersion, String strategy,
                       PlanFinding finding, String pomSha256, String baseCommitSha,
                       String status, List<PlanIssue> warnings, List<PlanIssue> errors,
                       Map<String, String> details, String errorMessage) {
    public PlanItem {
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
        errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
        details = Map.copyOf(Objects.requireNonNullElse(details, Map.of()));
        errorMessage = errorMessage == null || errorMessage.isBlank() ? null : errorMessage;
    }

    public PlanItem(String itemId, PlanProject project, PlanArtifact artifact,
                    String currentVersion, String targetVersion, String strategy,
                    PlanFinding finding, String pomSha256, String baseCommitSha,
                    String status, List<PlanIssue> warnings, List<PlanIssue> errors) {
        this(itemId, project, artifact, currentVersion, targetVersion, strategy, finding,
                pomSha256, baseCommitSha, status, warnings, errors, Map.of(), null);
    }

    public PlanItem(String itemId, PlanProject project, PlanArtifact artifact,
                    String currentVersion, String targetVersion, String strategy,
                    PlanFinding finding, String pomSha256, String baseCommitSha,
                    String status, List<PlanIssue> warnings, List<PlanIssue> errors,
                    Map<String, String> details) {
        this(itemId, project, artifact, currentVersion, targetVersion, strategy, finding,
                pomSha256, baseCommitSha, status, warnings, errors, details, null);
    }
}
