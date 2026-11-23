package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class StageRestartServiceTest {
    @Mock WorkspaceRepository workspaces;
    @org.mockito.Mock HistoryService history;
    @Test void removesStageAndDependentOutputsOnlyAfterConfirmation(@TempDir Path temp) throws Exception {
        UUID id = UUID.randomUUID(); when(workspaces.findById(id)).thenReturn(java.util.Optional.of(new WorkspaceEntity(id, temp.toString(), "READY", Instant.now(), Instant.now())));
        Path reports = temp.resolve("target/scafix/reports"); Files.createDirectories(reports); Files.writeString(reports.resolve("correlation.json"), "{}"); Files.writeString(reports.resolve("plan.json"), "{}");
        StageRestartService service = new StageRestartService(workspaces, history);
        assertTrue(Files.exists(reports.resolve("plan.json")));
        StageRestartService.RestartResult result = service.restart(id, "CORRELATION", true);
        assertFalse(Files.exists(reports.resolve("plan.json"))); assertTrue(result.removedOutputs().contains(Path.of("reports", "plan.json").toString()));
    }
}
