package pl.sca.fix.repository.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import pl.sca.fix.repository.application.RepositoryRequestValidator;
import pl.sca.fix.report.api.ReportDtos.SecurityScanReportMetadataDto;

public final class RepositoryDtos {
    private RepositoryDtos() { }

    public record RegisterLocalRepositoryRequest(@NotBlank String clonePath) {
        public RegisterLocalRepositoryRequest {
            RepositoryRequestValidator.validateClonePath(clonePath);
        }
    }

    public record RegisterGitRepositoryRequest(@NotBlank String repositoryUrl,
                                               String clonePath,
                                               String branch) {
        public RegisterGitRepositoryRequest {
            RepositoryRequestValidator.validateRepositoryUrl(repositoryUrl);
            if (clonePath != null && !clonePath.isBlank()) {
                RepositoryRequestValidator.validateClonePath(clonePath);
            }
        }
    }

    public record RegisterRepositoryResponse(UUID repositoryId, JobReferenceDto job, String proposedClonePath) { }

    public record JobReferenceDto(UUID jobId, String status) { }

    public record RepositorySummaryDto(UUID repositoryId, String name, String repositoryUrl,
                                       String clonePath, String defaultBranch, String baseCommitSha,
                                       long projectCount, long reportCount, long activePlanCount,
                                       String status) { }

    public record RepositoryDetailsDto(RepositorySummaryDto summary,
                                       List<Object> branches, List<Object> projects,
                                       List<SecurityScanReportMetadataDto> reports, List<Object> plans,
                                       Object lastJob) { }
}
