package pl.sca.fix.uibackend.workspace;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.discovery.ProjectDiscoveryRequest;
import pl.sca.fix.core.discovery.ProjectDiscoveryResult;
import pl.sca.fix.core.discovery.ProjectDiscoveryService;
import pl.sca.fix.core.model.ProjectModel;
import pl.sca.fix.uibackend.config.WorkspaceConfiguration;
import pl.sca.fix.uibackend.config.WorkspaceConfigurationService;

@Service
public class ProjectScopeService {
    private final WorkspaceRepository workspaces;
    private final ProjectDiscoveryService discovery;
    private final WorkspaceConfigurationService configurations;

    public ProjectScopeService(WorkspaceRepository workspaces, ProjectDiscoveryService discovery,
                               WorkspaceConfigurationService configurations) {
        this.workspaces = workspaces;
        this.discovery = discovery;
        this.configurations = configurations;
    }

    public List<ProjectView> discover(UUID workspaceId) {
        WorkspaceEntity workspace = workspace(workspaceId);
        WorkspaceConfiguration configuration = configurations.get(workspaceId);
        ProjectDiscoveryResult result = discovery.discover(ProjectDiscoveryRequest.cli(
                Path.of(workspace.getRepositoryPath()), List.of("*")));
        return result.projects().stream().map(project -> view(project, configuration)).toList();
    }

    public List<ProjectView> updateScope(UUID workspaceId, ScopeRequest request) {
        workspace(workspaceId);
        WorkspaceConfiguration current = configurations.get(workspaceId);
        configurations.updateScope(workspaceId, request.analysisProjects(), request.editableProjects());
        return discover(workspaceId);
    }

    private static ProjectView view(ProjectModel project, WorkspaceConfiguration configuration) {
        String coordinate = project.coordinate().groupId() + ":" + project.coordinate().artifactId();
        return new ProjectView(coordinate, project.coordinate().groupId(), project.coordinate().artifactId(),
                project.coordinate().version(), project.pomPath().toString().replace('\\', '/'),
                configuration.analysisProjects().contains("*") || configuration.analysisProjects().contains(coordinate),
                configuration.editableProjects().contains("*") || configuration.editableProjects().contains(coordinate));
    }

    private WorkspaceEntity workspace(UUID workspaceId) {
        return workspaces.findById(workspaceId).orElseThrow(() ->
                new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId));
    }

    public record ProjectView(String coordinate, String groupId, String artifactId, String version,
                              String pomPath, boolean analysisSelected, boolean editableSelected) {}
    public record ScopeRequest(List<String> analysisProjects, List<String> editableProjects) {}
}
