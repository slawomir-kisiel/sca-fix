package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportImportServiceTest {
    @Mock WorkspaceRepository workspaces;

    @Test
    void storesRawAndCanonicalReportWithSummary(@TempDir Path temp) throws Exception {
        UUID id = UUID.randomUUID();
        when(workspaces.findById(id)).thenReturn(java.util.Optional.of(
                new WorkspaceEntity(id, temp.toString(), "READY", Instant.now(), Instant.now())));
        byte[] content = "{\"dependencies\":[],\"projectInfo\":{}}".getBytes();

        ReportImportResultView result = new ReportImportService(workspaces)
                .importReport(id, "scan.json", content, "OWASP");

        assertEquals("OWASP", result.format());
        assertEquals(content.length, result.sizeBytes());
        assertTrue(Files.exists(Path.of(result.rawPath())));
        assertTrue(Files.exists(Path.of(result.canonicalPath())));
    }
}
