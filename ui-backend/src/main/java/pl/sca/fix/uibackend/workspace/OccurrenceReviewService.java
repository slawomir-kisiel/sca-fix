package pl.sca.fix.uibackend.workspace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.report.CorrelationEntry;
import pl.sca.fix.core.report.CorrelationJsonCodec;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.DependencyTreeOccurrence;

@Service
public class OccurrenceReviewService {
    private final WorkspaceRepository workspaces;
    private final ObjectMapper mapper;

    public OccurrenceReviewService(WorkspaceRepository workspaces, ObjectMapper mapper) {
        this.workspaces = workspaces; this.mapper = mapper;
    }

    public ReviewResponse get(UUID workspaceId) {
        WorkspaceEntity workspace = workspace(workspaceId);
        CorrelationResult correlation = loadCorrelation(workspace);
        List<OccurrenceDecision> decisions = loadDecisions(workspace);
        List<ReviewEntry> entries = correlation.entries().stream().map(entry -> new ReviewEntry(
                entry.libraryKey(), entry.status().name(), entry.warnings(),
                entry.occurrences().stream().map(occurrence -> occurrence(occurrence, decisions)).toList())).toList();
        return new ReviewResponse(workspaceId, entries, correlation.warnings(), correlation.errors());
    }

    public ReviewResponse decide(UUID workspaceId, UUID occurrenceId, String action) {
        WorkspaceEntity workspace = workspace(workspaceId);
        List<OccurrenceDecision> decisions = loadDecisions(workspace);
        decisions.removeIf(decision -> decision.occurrenceId().equals(occurrenceId));
        decisions.add(new OccurrenceDecision(occurrenceId, action == null || action.isBlank() ? "CONTEXT" : action.toUpperCase(), Instant.now()));
        saveDecisions(workspace, decisions);
        return get(workspaceId);
    }

    private OccurrenceView occurrence(DependencyTreeOccurrence occurrence, List<OccurrenceDecision> decisions) {
        String action = decisions.stream().filter(decision -> decision.occurrenceId().equals(UUID.nameUUIDFromBytes(
                occurrence.pathId().getBytes(java.nio.charset.StandardCharsets.UTF_8)))).map(OccurrenceDecision::action).findFirst().orElse(null);
        UUID id = UUID.nameUUIDFromBytes(occurrence.pathId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new OccurrenceView(id, occurrence.projectId(), occurrence.pathId(), occurrence.vulnerableNodeId(),
                occurrence.resolvedVersion(), occurrence.depth(), occurrence.previousNodeId(), action);
    }

    private CorrelationResult loadCorrelation(WorkspaceEntity workspace) {
        try {
            Path path = Path.of(workspace.getRepositoryPath()).resolve("target/scafix/reports/correlation.json");
            if (!Files.exists(path)) throw new WorkspaceException("CORRELATION_REQUIRED", "Run correlation before occurrence review");
            return new CorrelationJsonCodec().read(Files.readAllBytes(path));
        } catch (WorkspaceException exception) { throw exception; }
        catch (Exception exception) { throw new WorkspaceException("CORRELATION_READ_FAILED", exception.getMessage()); }
    }

    private List<OccurrenceDecision> loadDecisions(WorkspaceEntity workspace) {
        try {
            Path path = decisionsPath(workspace);
            if (!Files.exists(path)) return new ArrayList<>();
            return new ArrayList<>(mapper.readValue(Files.readAllBytes(path), new TypeReference<List<OccurrenceDecision>>() {}));
        } catch (Exception exception) { throw new WorkspaceException("OCCURRENCE_DECISIONS_READ_FAILED", exception.getMessage()); }
    }

    private void saveDecisions(WorkspaceEntity workspace, List<OccurrenceDecision> decisions) {
        try {
            Path path = decisionsPath(workspace); Files.createDirectories(path.getParent());
            Files.write(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(decisions));
        } catch (Exception exception) { throw new WorkspaceException("OCCURRENCE_DECISIONS_WRITE_FAILED", exception.getMessage()); }
    }

    private static Path decisionsPath(WorkspaceEntity workspace) {
        return Path.of(workspace.getRepositoryPath()).resolve("target/scafix/reports/occurrence-decisions.json");
    }
    private WorkspaceEntity workspace(UUID id) { return workspaces.findById(id).orElseThrow(() ->
            new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + id)); }

    public record ReviewResponse(UUID workspaceId, List<ReviewEntry> entries, List<String> warnings, List<String> errors) {}
    public record ReviewEntry(String libraryKey, String status, List<String> warnings, List<OccurrenceView> occurrences) {}
    public record OccurrenceView(UUID occurrenceId, String projectId, String pathId, String vulnerableNodeId,
                                 String reportedVersion, int depth, String previousNodeId, String action) {}
    public record OccurrenceDecision(UUID occurrenceId, String action, Instant selectedAt) {}
}
