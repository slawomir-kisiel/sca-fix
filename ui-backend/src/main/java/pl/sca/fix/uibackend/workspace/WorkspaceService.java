package pl.sca.fix.uibackend.workspace;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.port.GitStatus;
import pl.sca.fix.core.port.GitPort;
@Service
public class WorkspaceService {
    private final WorkspaceRepository repository;
    private final GitPort git;
    public WorkspaceService(WorkspaceRepository repository, GitPort git) {
        this.repository = repository;
        this.git = git;
    }
    public WorkspaceResponse createLocal(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank())
            throw new WorkspaceException("REPOSITORY_PATH_REQUIRED", "Repository path is required");
        Path path = Path.of(requestedPath.trim()).toAbsolutePath().normalize();
        GitStatus status = git.status(path);
        if (status == null || !status.repository())
            throw new WorkspaceException("NOT_A_GIT_REPOSITORY", "Path is not a Git repository: " + path);
        Instant now = Instant.now();
        WorkspaceEntity entity = repository.save(new WorkspaceEntity(UUID.randomUUID(), path.toString(),
                "READY", now, now));
        return WorkspaceResponse.from(entity, status);
    }
    public WorkspaceResponse get(UUID id) {
        WorkspaceEntity entity = repository.findById(id).orElseThrow(() ->
                new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + id));
        return WorkspaceResponse.from(entity, git.status(Path.of(entity.getRepositoryPath())));
    }
}

