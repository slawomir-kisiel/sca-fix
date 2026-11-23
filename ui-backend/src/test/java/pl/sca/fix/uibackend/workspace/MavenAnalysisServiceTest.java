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
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.core.port.MavenExecutionResult;
import pl.sca.fix.core.port.ProcessResult;
import java.time.Duration;
import pl.sca.fix.uibackend.config.WorkspaceConfigurationService;

@ExtendWith(MockitoExtension.class)
class MavenAnalysisServiceTest {
    @Mock ProjectDiscoveryService discovery;
    @Mock WorkspaceRepository workspaces;
    @Mock MavenPort maven;
    @Mock WorkspaceConfigurationService configurations;
    @InjectMocks MavenAnalysisService service;

    @Test
    void queuesSelectedProjects() throws InterruptedException {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceEntity workspace = new WorkspaceEntity(workspaceId, "C:/checkout", "READY", Instant.now(), Instant.now());
        ProjectModel project = new ProjectModel(new ArtifactCoordinate("com.example", "service", "1.0.0"),
                Path.of("service/pom.xml"));
        when(workspaces.findById(workspaceId)).thenReturn(java.util.Optional.of(workspace));
        when(discovery.discover(org.mockito.ArgumentMatchers.any())).thenReturn(new ProjectDiscoveryResult(
                List.of(project), new ProjectScope(List.of(project), List.of(project)), java.util.Map.of()));
        var process = new ProcessResult(0, "", "", Duration.ZERO, false);
        when(maven.generateEffectivePom(org.mockito.ArgumentMatchers.any())).thenReturn(
                new MavenExecutionResult(process, Path.of("effective-pom.xml")));
        when(maven.generateDependencyTree(org.mockito.ArgumentMatchers.any())).thenReturn(
                new MavenExecutionResult(process, Path.of("dependency-tree.json")));

        List<MavenAnalysisJob> jobs = service.start(workspaceId, List.of("com.example:service"));

        assertEquals(1, jobs.size());
        for (int attempt = 0; attempt < 20 && "QUEUED".equals(service.get(jobs.getFirst().jobId()).status()); attempt++) {
            Thread.sleep(10);
        }
        assertEquals("COMPLETED", service.get(jobs.getFirst().jobId()).status());
        assertEquals("com.example:service:1.0.0", jobs.getFirst().projectCoordinate());
    }
}
