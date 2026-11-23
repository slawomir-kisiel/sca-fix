package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {
    @Mock WorkspaceRepository workspaces;
    @Test void downloadsOnlyFilesInsideScafixDirectory(@TempDir Path temp) throws Exception {
        UUID id = UUID.randomUUID(); when(workspaces.findById(id)).thenReturn(java.util.Optional.of(new WorkspaceEntity(id, temp.toString(), "READY", Instant.now(), Instant.now())));
        Path file = temp.resolve("target/scafix/reports/plan.json"); Files.createDirectories(file.getParent()); Files.writeString(file, "{}");
        ArtifactService.ArtifactResource result = new ArtifactService(workspaces).get(id, "reports/plan.json");
        assertEquals("plan.json", result.fileName());
        assertThrows(WorkspaceException.class, () -> new ArtifactService(workspaces).get(id, "../../secret.txt"));
    }
}
