package pl.sca.fix.report.api;

import java.util.List;
import java.util.UUID;
import pl.sca.fix.report.domain.UpdateStrategy;

public final class StrategyDtos {
    private StrategyDtos() { }

    public record StrategyOptionDto(UpdateStrategy strategy, String label, boolean eligible,
                                    String rejectionReason) { }

    public record StrategyImpactDto(long occurrenceCount, long otherOccurrenceCount,
                                    String warning) { }

    public record VersionDefinitionDto(boolean found, String sourceProject,
                                       String sourcePomPath, String section,
                                       String propertyName, Integer sourceLine,
                                       String message) { }

    public record StrategyOptionsDto(UUID findingId, UUID occurrenceId, UUID targetArtifactVersionId,
                                     List<StrategyOptionDto> options, StrategyImpactDto impact,
                                     UpdateStrategy selectedStrategy,
                                     UpdateStrategy recommendedStrategy,
                                     VersionDefinitionDto versionDefinition) { }

}
