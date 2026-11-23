package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.sca.fix.core.port.GitClonePort;
import pl.sca.fix.core.port.GitPort;

@ExtendWith(MockitoExtension.class)
class GitCloneServiceTest {
    @Mock WorkspaceRepository workspaces;
    @Mock GitClonePort gitClone;
    @Mock GitPort git;
    @InjectMocks GitCloneService service;

    @Test
    void proposesRepositoryNameUnderDefaultWorkDirectory() {
        GitCloneService.ClonePlan plan = service.plan("https://git.example/team/sca-fix-tool.git", null);

        assertTrue(plan.proposedClonePath().endsWith(
                Path.of("work", "sca-fix-tool").toString()));
    }

}

