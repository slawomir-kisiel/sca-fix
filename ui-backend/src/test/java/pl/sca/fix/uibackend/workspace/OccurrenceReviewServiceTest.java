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
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import pl.sca.fix.core.report.CorrelationJsonCodec;
import pl.sca.fix.core.report.CorrelationResult;

@ExtendWith(MockitoExtension.class)
class OccurrenceReviewServiceTest {
    @Mock WorkspaceRepository workspaces;

    @Test
    void readsCorrelationForReview(@TempDir Path temp) throws Exception {
        UUID id = UUID.randomUUID();
        when(workspaces.findById(id)).thenReturn(java.util.Optional.of(
                new WorkspaceEntity(id, temp.toString(), "READY", Instant.now(), Instant.now())));
        Path reports = temp.resolve("target/scafix/reports"); Files.createDirectories(reports);
        Files.write(reports.resolve("correlation.json"), new CorrelationJsonCodec().write(
                new CorrelationResult(1, List.of(), List.of(), List.of())));

        var response = new OccurrenceReviewService(workspaces, new ObjectMapper()).get(id);

        assertEquals(id, response.workspaceId());
        assertEquals(0, response.entries().size());
    }
}
