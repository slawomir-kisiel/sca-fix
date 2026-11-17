package pl.sca.fix.report.api;

import java.time.Instant;
import java.util.UUID;

public final class TargetVersionDtos {
    private TargetVersionDtos() { }

    public record TargetVersionOptionDto(UUID artifactVersionId, String version, boolean current,
                                         boolean eligible, String rejectionReason,
                                         String source, String repositoryId, String webUrl) { }

    public record TargetContextDto(String repositoryId, String repositoryUrl, String projectName, String pomPath,
                                   String groupId, String artifactId, String reportedVersion,
                                   String detectedVersion) { }

    public record VersionRuleDto(UUID artifactId, String pattern, boolean allowSnapshots) { }

    public record TargetVersionsDto(UUID findingId, UUID occurrenceId, String reportedVersion,
                                    String currentVersion, VersionRuleDto rule,
                                    java.util.List<TargetVersionOptionDto> versions,
                                    UUID selectedArtifactVersionId, java.util.List<String> warnings,
                                    TargetContextDto context) { }

    public record SelectTargetVersionRequest(UUID occurrenceId, UUID targetArtifactVersionId) { }

    public record TargetVersionSelectionDto(UUID findingId, UUID occurrenceId,
                                            UUID targetArtifactVersionId, Instant selectedAt) { }
}
