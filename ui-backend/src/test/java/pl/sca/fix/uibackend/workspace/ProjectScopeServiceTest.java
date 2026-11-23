package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.sca.fix.core.discovery.ProjectDiscoveryResult;
import pl.sca.fix.core.discovery.ProjectDiscoveryService;
import pl.sca.fix.core.model.ArtifactCoordinate;
import pl.sca.fix.core.model.ProjectModel;
import pl.sca.fix.core.model.ProjectScope;
import pl.sca.fix.uibackend.config.WorkspaceConfiguration;
import pl.sca.fix.uibackend.config.WorkspaceConfigurationService;

@ExtendWith(MockitoExtension.class)
class ProjectScopeServiceTest {
    @Mock WorkspaceRepository workspaces;
    @Mock ProjectDiscoveryService discovery;
    @Mock WorkspaceConfigurationService configurations;
    @InjectMocks ProjectScopeService service;

    @Test
    void returnsProjectsWithIndependentAnalysisAndEditSelection() {
        UUID id = UUID.randomUUID();
        WorkspaceEntity workspace = new WorkspaceEntity(id, "C:/checkout", "READY", Instant.now(), Instant.now());
        ProjectModel project = new ProjectModel(new ArtifactCoordinate("com.example", "service", "1.0.0"),
                Path.of("service/pom.xml"));
        ProjectDiscoveryResult result = new ProjectDiscoveryResult(List.of(project),
                new ProjectScope(List.of(project), List.of(project)), java.util.Map.of());
        when(workspaces.findById(id)).thenReturn(java.util.Optional.of(workspace));
        when(configurations.get(id)).thenReturn(new WorkspaceConfiguration(1, List.of("com.example:service"),
                List.of(), "MINOR", "PATCH", "SHALLOWEST", List.of(), List.of(), null, null, List.of(), false));
        when(discovery.discover(org.mockito.ArgumentMatchers.any())).thenReturn(result);

        var projects = service.discover(id);

        assertEquals(1, projects.size());
        assertEquals("com.example:service", projects.getFirst().coordinate());
        assertEquals(true, projects.getFirst().analysisSelected());
        assertEquals(false, projects.getFirst().editableSelected());
    }
}
