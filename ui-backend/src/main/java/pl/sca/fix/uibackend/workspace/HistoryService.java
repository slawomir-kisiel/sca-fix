package pl.sca.fix.uibackend.workspace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read-only workspace history backed by an append-only JSON event file and current artifacts. */
@Service
public class HistoryService {
    private final WorkspaceRepository workspaces;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    public HistoryService(WorkspaceRepository workspaces) { this.workspaces = workspaces; }
    public HistorySnapshot get(UUID workspaceId) {
        WorkspaceEntity workspace = workspaces.findById(workspaceId).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId));
        Path root = Path.of(workspace.getRepositoryPath()).resolve("target/scafix");
        try {
            List<HistoryEvent> events = Files.exists(root.resolve("history.json")) ? mapper.readValue(Files.readAllBytes(root.resolve("history.json")), new TypeReference<>() { }) : List.of();
            List<Artifact> artifacts = Files.exists(root) ? Files.walk(root).filter(Files::isRegularFile).filter(path -> !path.endsWith("history.json")).map(path -> new Artifact(root.relativize(path).toString(), size(path), modified(path))).sorted(Comparator.comparing(Artifact::path)).toList() : List.of();
            return new HistorySnapshot(workspaceId, workspace.getRepositoryPath(), workspace.getStatus(), events, artifacts);
        } catch (Exception exception) { throw new WorkspaceException("HISTORY_READ_FAILED", exception.getMessage()); }
    }
    public void append(UUID workspaceId, String stage, String action, String details) {
        WorkspaceEntity workspace = workspaces.findById(workspaceId).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId));
        Path root = Path.of(workspace.getRepositoryPath()).resolve("target/scafix");
        try {
            Files.createDirectories(root); Path file = root.resolve("history.json");
            List<HistoryEvent> events = Files.exists(file) ? new ArrayList<>(mapper.readValue(Files.readAllBytes(file), new TypeReference<>() { })) : new ArrayList<>();
            events.add(new HistoryEvent(UUID.randomUUID().toString(), Instant.now(), stage, action, details));
            Files.write(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(events));
        } catch (Exception exception) { throw new WorkspaceException("HISTORY_WRITE_FAILED", exception.getMessage()); }
    }
    private static long size(Path path) { try { return Files.size(path); } catch (Exception exception) { return -1; } }
    private static Instant modified(Path path) { try { return Files.getLastModifiedTime(path).toInstant(); } catch (Exception exception) { return null; } }
    public record HistorySnapshot(UUID workspaceId, String repositoryPath, String workspaceStatus, List<HistoryEvent> events, List<Artifact> artifacts) { }
    public record HistoryEvent(String eventId, Instant timestamp, String stage, String action, String details) { }
    public record Artifact(String path, long sizeBytes, Instant modifiedAt) { }
}
