package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import pl.sca.fix.core.report.CorrelationJsonCodec;
import pl.sca.fix.core.report.CorrelationResult;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChangeStrategyServiceTest {
    @Mock WorkspaceRepository workspaces;

    @Test
    void rejectsUnknownOccurrence(@TempDir Path temp) throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaces.findById(workspaceId)).thenReturn(java.util.Optional.of(new WorkspaceEntity(workspaceId, temp.toString(), "READY", Instant.now(), Instant.now())));
        Path reports = temp.resolve("target/scafix/reports"); Files.createDirectories(reports);
        Files.write(reports.resolve("correlation.json"), new CorrelationJsonCodec().write(new CorrelationResult(1, List.of(), List.of(), List.of())));
        var exception = org.junit.jupiter.api.Assertions.assertThrows(WorkspaceException.class, () -> new ChangeStrategyService(workspaces, new ObjectMapper()).options(workspaceId, UUID.randomUUID()));
        assertEquals("OCCURRENCE_NOT_FOUND", exception.code());
    }
}
