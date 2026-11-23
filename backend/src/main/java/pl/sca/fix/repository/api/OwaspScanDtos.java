package pl.sca.fix.repository.api;

import java.util.List;
import java.util.UUID;

public final class OwaspScanDtos {
    private OwaspScanDtos() { }

    public record ScanScopeDto(UUID scopeId, UUID rootProjectId, String projectName,
                               String pomPath, String groupId, String artifactId,
                               String version, boolean aggregator,
                               List<UUID> coveredProjectIds, List<String> coveredPomPaths) { }

    public record ScanPlanDto(UUID repositoryId, String pluginVersion,
                              String latestPluginVersion, List<ScanScopeDto> scopes,
                              List<String> warnings) { }

    public record StartScanRequest(List<UUID> scopeIds) { }

    public record ScopeJobDto(UUID scopeId, UUID projectId, UUID jobId,
                              String status, String pomPath) { }

    public record ScanStartResponse(UUID repositoryId, String pluginVersion,
                                    List<ScopeJobDto> jobs) { }
}
