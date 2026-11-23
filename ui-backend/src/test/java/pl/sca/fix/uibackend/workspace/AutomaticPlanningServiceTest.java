package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import pl.sca.fix.core.discovery.ProjectDiscoveryService;
import pl.sca.fix.uibackend.config.WorkspaceConfigurationService;

@ExtendWith(MockitoExtension.class)
class AutomaticPlanningServiceTest {
    @Mock WorkspaceRepository workspaces; @Mock ProjectDiscoveryService discovery; @Mock WorkspaceConfigurationService configurations;
    @Test void queuesPlanningJob() {
        UUID id = UUID.randomUUID(); when(workspaces.findById(id)).thenReturn(java.util.Optional.of(new WorkspaceEntity(id, Path.of(".").toAbsolutePath().toString(), "READY", Instant.now(), Instant.now())));
        PlanningJob job = new AutomaticPlanningService(workspaces, discovery, configurations).start(id, java.util.List.of());
        assertEquals("QUEUED", job.status());
    }
}
