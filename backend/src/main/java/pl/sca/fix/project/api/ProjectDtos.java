package pl.sca.fix.project.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public final class ProjectDtos {
    private ProjectDtos() { }

    public record ProjectCandidateDto(String pomPath, String groupId, String artifactId, String version,
                                      String packaging, ParentDto parent, List<String> modules,
                                      boolean registered, String error) { }

    public record ParentDto(String groupId, String artifactId, String version) { }

    public record ProjectSummaryDto(UUID projectId, UUID repositoryId, String name, String pomPath,
                                    String groupId, String artifactId, String version, String packaging,
                                    String status, String mavenAnalysisStatus, UUID mavenAnalysisJobId,
                                    Instant mavenAnalysisStartedAt, Instant mavenAnalysisFinishedAt) { }

    public record ProjectDiscoveryResponse(UUID repositoryId, List<ProjectCandidateDto> candidates) { }

    public record RegisterProjectsRequest(@NotEmpty List<String> pomPaths) { }

    public record RegisterProjectsResponse(List<ProjectSummaryDto> projects) { }
}
