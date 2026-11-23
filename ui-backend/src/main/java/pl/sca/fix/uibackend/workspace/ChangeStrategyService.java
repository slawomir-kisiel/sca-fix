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
import pl.sca.fix.core.report.CanonicalReportJsonCodec;
import pl.sca.fix.core.report.CorrelationJsonCodec;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.VulnerableLibrary;

@Service
public class ChangeStrategyService {
    private final WorkspaceRepository workspaces;
    private final ObjectMapper mapper;

    public ChangeStrategyService(WorkspaceRepository workspaces, ObjectMapper mapper) {
        this.workspaces = workspaces; this.mapper = mapper;
    }

    public StrategyOptions options(UUID workspaceId, UUID occurrenceId) {
        WorkspaceEntity workspace = workspace(workspaceId);
        CorrelationResult correlation = correlation(workspace);
        String libraryKey = null;
        String projectId = null;
        String nodeId = null;
        String currentVersion = null;
        for (var entry : correlation.entries()) {
            for (var occurrence : entry.occurrences()) {
                UUID id = UUID.nameUUIDFromBytes(occurrence.pathId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (id.equals(occurrenceId)) {
                    libraryKey = entry.libraryKey(); projectId = occurrence.projectId(); nodeId = occurrence.vulnerableNodeId();
                    currentVersion = occurrence.resolvedVersion();
                }
            }
        }
        if (libraryKey == null) throw new WorkspaceException("OCCURRENCE_NOT_FOUND", "Occurrence not found: " + occurrenceId);
        List<String> versions = new ArrayList<>();
        for (VulnerableLibrary library : new CanonicalReportJsonCodec().read(read(workspace, "canonical-report.json")).vulnerableLibraries()) {
            if ((library.groupId() + ":" + library.artifactId() + ":" + library.version()).equals(libraryKey)) {
                versions.add(library.version()); versions.addAll(library.recommendedVersion());
            }
        }
        return new StrategyOptions(occurrenceId, libraryKey, projectId, nodeId, currentVersion,
                versions.stream().distinct().toList(), List.of("DIRECT_DEPENDENCY", "DEPENDENCY_MANAGEMENT", "PROPERTY", "CVE_OVERRIDE"),
                load(workspace));
    }

    public StrategyDecision save(UUID workspaceId, UUID occurrenceId, StrategyRequest request) {
        WorkspaceEntity workspace = workspace(workspaceId);
        StrategyOptions options = options(workspaceId, occurrenceId);
        if (!options.strategies().contains(request.strategy())) throw new WorkspaceException("STRATEGY_NOT_SUPPORTED", "Unsupported strategy: " + request.strategy());
        if (request.targetVersion() == null || request.targetVersion().isBlank()) throw new WorkspaceException("TARGET_VERSION_REQUIRED", "Target version is required");
        List<StrategyDecision> decisions = load(workspace); decisions.removeIf(item -> item.occurrenceId().equals(occurrenceId));
        StrategyDecision decision = new StrategyDecision(occurrenceId, options.projectId(), options.nodeId(), request.strategy(), request.targetVersion(), Instant.now());
        decisions.add(decision); save(workspace, decisions); return decision;
    }

    private List<StrategyDecision> load(WorkspaceEntity workspace) {
        try { Path path = path(workspace); return Files.exists(path) ? new ArrayList<>(mapper.readValue(Files.readAllBytes(path), new TypeReference<List<StrategyDecision>>() {})) : new ArrayList<>(); }
        catch (Exception exception) { throw new WorkspaceException("STRATEGY_DECISIONS_READ_FAILED", exception.getMessage()); }
    }
    private void save(WorkspaceEntity workspace, List<StrategyDecision> decisions) { try { Path path = path(workspace); Files.createDirectories(path.getParent()); Files.write(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(decisions)); } catch (Exception exception) { throw new WorkspaceException("STRATEGY_DECISIONS_WRITE_FAILED", exception.getMessage()); } }
    private static Path path(WorkspaceEntity workspace) { return Path.of(workspace.getRepositoryPath()).resolve("target/scafix/reports/strategy-decisions.json"); }
    private static byte[] read(WorkspaceEntity workspace, String file) { try { return Files.readAllBytes(Path.of(workspace.getRepositoryPath()).resolve("target/scafix/reports").resolve(file)); } catch (Exception exception) { throw new WorkspaceException("REPORT_READ_FAILED", exception.getMessage()); } }
    private static CorrelationResult correlation(WorkspaceEntity workspace) { try { return new CorrelationJsonCodec().read(read(workspace, "correlation.json")); } catch (Exception exception) { throw new WorkspaceException("CORRELATION_READ_FAILED", exception.getMessage()); } }
    private WorkspaceEntity workspace(UUID id) { return workspaces.findById(id).orElseThrow(() -> new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + id)); }

    public record StrategyOptions(UUID occurrenceId, String libraryKey, String projectId, String nodeId,
                                  String currentVersion, List<String> targetVersions, List<String> strategies,
                                  List<StrategyDecision> savedDecision) {}
    public record StrategyRequest(String strategy, String targetVersion) {}
    public record StrategyDecision(UUID occurrenceId, String targetProjectId, String changePointTreeNodeId,
                                   String strategy, String targetVersion, Instant selectedAt) {}
}
