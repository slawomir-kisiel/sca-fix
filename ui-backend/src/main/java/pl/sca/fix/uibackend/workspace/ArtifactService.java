package pl.sca.fix.uibackend.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Service;

@Service
public class ArtifactService {
    private final WorkspaceRepository workspaces;
    public ArtifactService(WorkspaceRepository workspaces) { this.workspaces = workspaces; }
    public ArtifactResource get(UUID workspaceId, String relativePath) {
        WorkspaceEntity workspace = workspaces.findById(workspaceId).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId));
        if (relativePath == null || relativePath.isBlank()) throw new WorkspaceException("ARTIFACT_PATH_REQUIRED", "Artifact path is required");
        Path root = Path.of(workspace.getRepositoryPath()).resolve("target/scafix").toAbsolutePath().normalize();
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) throw new WorkspaceException("ARTIFACT_PATH_INVALID", "Artifact path escapes workspace output directory");
        if (!Files.isRegularFile(file)) throw new WorkspaceException("ARTIFACT_NOT_FOUND", "Artifact not found: " + relativePath);
        try { return new ArtifactResource(file.getFileName().toString(), Files.probeContentType(file), Files.size(file), new PathResource(file)); }
        catch (IOException exception) { throw new WorkspaceException("ARTIFACT_READ_FAILED", exception.getMessage()); }
    }
    public record ArtifactResource(String fileName, String contentType, long size, Resource resource) { }
}
