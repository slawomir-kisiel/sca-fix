package pl.sca.fix.uibackend.workspace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.sca.fix.core.port.GitStatus;
public record WorkspaceResponse(UUID workspaceId, String repositoryPath, String status, String branch,
                                String commitSha, boolean clean, List<String> changedFiles,
                                Instant createdAt, Instant updatedAt) {
    static WorkspaceResponse from(WorkspaceEntity entity, GitStatus git) {
        return new WorkspaceResponse(entity.getId(), entity.getRepositoryPath(), entity.getStatus(),
                git == null ? null : git.branch(), git == null ? null : git.commitSha(),
                git != null && git.clean(),
                git == null ? List.of() : git.changedFiles().stream().map(Object::toString).toList(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}

