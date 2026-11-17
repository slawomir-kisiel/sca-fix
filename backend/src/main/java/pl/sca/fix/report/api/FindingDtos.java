package pl.sca.fix.report.api;

import java.util.List;
import java.util.UUID;

public final class FindingDtos {
    private FindingDtos() { }

    public record AffectedArtifactDto(UUID artifactId, String purl, String groupId, String artifactIdValue,
                                      String reportedVersion, String fileName, String filePath,
                                      String sha1, String sha256) { }

    public record FindingListItemDto(UUID findingId, String externalId, String identifierType,
                                     String source, String severity, Double cvssScore,
                                     String artifactCoordinates, String reportedVersion,
                                     long occurrenceCount, String correlationStatus) { }

    public record VulnerableArtifactGroupDto(String groupId, String artifactId, String version,
                                             long occurrenceCount, long findingCount,
                                             Double maxCvssScore, String maxCvssSeverity,
                                             UUID maxCvssFindingId,
                                             PlanItemLinkDto planItem,
                                             List<FindingGroupItemDto> findings) { }

    public record PlanItemLinkDto(UUID itemId, UUID planId, UUID findingId, UUID occurrenceId,
                                  UUID targetProjectId, UUID changePointTreeNodeId,
                                  UUID targetArtifactVersionId, String strategy, String status) { }

    public record FindingGroupItemDto(UUID findingId, String cve, String externalId,
                                      String identifierType, String title, String source,
                                      String severity, Double cvssScore, long occurrenceCount,
                                      String correlationStatus, PlanItemLinkDto planItem) { }

    public record FindingDetailsDto(UUID findingId, String externalId, String identifierType,
                                    String source, String cve, String title, String severity,
                                    String description, String recommendation, String cvssVersion,
                                    Double cvssScore, String cvssVector, List<String> cwes,
                                    List<String> references, List<AffectedArtifactDto> affectedArtifacts,
                                    List<OccurrenceDto> occurrences, String correlationStatus) { }

    public record OccurrenceDto(UUID occurrenceId, UUID projectId, UUID artifactVersionId,
                                String resolvedVersion, String definitionSource,
                                boolean sourcesAvailable) { }
}
