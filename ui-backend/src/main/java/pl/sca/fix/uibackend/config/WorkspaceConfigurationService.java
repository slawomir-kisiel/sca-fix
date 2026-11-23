package pl.sca.fix.uibackend.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import pl.sca.fix.uibackend.workspace.WorkspaceException;
import pl.sca.fix.uibackend.workspace.WorkspaceRepository;

@Service
public class WorkspaceConfigurationService {
    private final WorkspaceConfigurationRepository configurations;
    private final WorkspaceRepository workspaces;
    private final ObjectMapper mapper;

    public WorkspaceConfigurationService(WorkspaceConfigurationRepository configurations,
                                         WorkspaceRepository workspaces, ObjectMapper mapper) {
        this.configurations = configurations; this.workspaces = workspaces; this.mapper = mapper;
    }

    public WorkspaceConfiguration get(UUID workspaceId) {
        requireWorkspace(workspaceId);
        return configurations.findById(workspaceId).map(this::read)
                .orElseGet(WorkspaceConfiguration::defaults);
    }

    public WorkspaceConfiguration update(UUID workspaceId, WorkspaceConfiguration configuration) {
        requireWorkspace(workspaceId);
        WorkspaceConfiguration normalized = new WorkspaceConfiguration(configuration.schemaVersion(),
                configuration.analysisProjects(), configuration.editableProjects(), configuration.updateLevel(),
                configuration.cveOverrideUpdateLevel(), configuration.ambiguousStrategy(),
                configuration.skipLibraries(), configuration.disabledStrategies(), configuration.proxyHost(),
                configuration.proxyPort(), configuration.mavenRepositories(), configuration.ignoreLocalChanges());
        try {
            configurations.save(new WorkspaceConfigurationEntity(workspaceId, mapper.writeValueAsString(normalized),
                    Instant.now()));
            return normalized;
        } catch (java.io.IOException exception) {
            throw new WorkspaceException("CONFIGURATION_SERIALIZATION_FAILED", exception.getMessage());
        }
    }

    public WorkspaceConfiguration updateScope(UUID workspaceId, List<String> analysisProjects,
                                              List<String> editableProjects) {
        WorkspaceConfiguration current = get(workspaceId);
        return update(workspaceId, new WorkspaceConfiguration(current.schemaVersion(), analysisProjects,
                editableProjects, current.updateLevel(), current.cveOverrideUpdateLevel(),
                current.ambiguousStrategy(), current.skipLibraries(), current.disabledStrategies(),
                current.proxyHost(), current.proxyPort(), current.mavenRepositories(), current.ignoreLocalChanges()));
    }

    public byte[] export(UUID workspaceId) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(get(workspaceId));
        } catch (java.io.IOException exception) {
            throw new WorkspaceException("CONFIGURATION_SERIALIZATION_FAILED", exception.getMessage());
        }
    }

    public WorkspaceConfiguration importConfiguration(UUID workspaceId, byte[] content) {
        requireWorkspace(workspaceId);
        if (content == null || content.length == 0) {
            throw new WorkspaceException("CONFIGURATION_FILE_EMPTY", "Configuration file is empty");
        }
        try {
            WorkspaceConfiguration imported = mapper.readValue(content, WorkspaceConfiguration.class);
            if (imported.schemaVersion() != WorkspaceConfiguration.defaults().schemaVersion()) {
                throw new WorkspaceException("CONFIGURATION_SCHEMA_UNSUPPORTED",
                        "Unsupported configuration schema version: " + imported.schemaVersion());
            }
            return update(workspaceId, imported);
        } catch (WorkspaceException exception) {
            throw exception;
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new WorkspaceException("CONFIGURATION_INVALID", exception.getMessage());
        }
    }

    private WorkspaceConfiguration read(WorkspaceConfigurationEntity entity) {
        try { return mapper.readValue(entity.getContent(), WorkspaceConfiguration.class); }
        catch (JsonProcessingException exception) {
            throw new WorkspaceException("CONFIGURATION_READ_FAILED", exception.getMessage());
        }
    }

    private void requireWorkspace(UUID workspaceId) {
        if (!workspaces.existsById(workspaceId)) {
            throw new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId);
        }
    }
}

