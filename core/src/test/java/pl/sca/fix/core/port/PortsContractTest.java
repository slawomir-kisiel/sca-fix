package pl.sca.fix.core.port;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortsContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void processRequestIsImmutableAndRendersTheCommand() {
        ProcessRequest request = new ProcessRequest(List.of("mvn", "-f", "path with spaces/pom.xml"),
                temporaryDirectory, null, Duration.ofSeconds(10));

        assertEquals("mvn -f \"path with spaces/pom.xml\"", request.displayCommand());
        assertEquals(Duration.ofSeconds(10), request.timeout());
    }

    @Test
    void processResultReportsSuccessOnlyForZeroExitCodeWithoutTimeout() {
        assertTrue(new ProcessResult(0, "out", "", Duration.ZERO, false).successful());
        assertFalse(new ProcessResult(1, "", "error", Duration.ofMillis(1), false).successful());
        assertFalse(new ProcessResult(0, "", "", Duration.ofMillis(1), true).successful());
    }

    @Test
    void documentStoresProtectMutableBytes() {
        byte[] bytes = "state".getBytes(StandardCharsets.UTF_8);
        StoredDocument document = new StoredDocument("ws-1", bytes);
        bytes[0] = 'X';

        assertArrayEquals("state".getBytes(StandardCharsets.UTF_8), document.content());
        byte[] returned = document.content();
        returned[0] = 'X';
        assertArrayEquals("state".getBytes(StandardCharsets.UTF_8), document.content());
    }

    @Test
    void inMemoryStoreImplementsWorkspaceAndPlanContracts() {
        PortTestDoubles.InMemoryDocumentStore store = new PortTestDoubles.InMemoryDocumentStore();
        StoredDocument document = new StoredDocument("plan-1", new byte[] {1, 2});

        store.save(document);

        assertTrue(store.exists("plan-1"));
        assertNotNull(store.load("plan-1").orElseThrow());
        store.delete("plan-1");
        assertFalse(store.exists("plan-1"));
    }

    @Test
    void temporaryFileSystemProvidesDeterministicMetadataAndAtomicWrites() throws Exception {
        PortTestDoubles.TempFileSystem fileSystem = new PortTestDoubles.TempFileSystem();
        Path file = temporaryDirectory.resolve("nested/file.txt");
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);

        fileSystem.writeAtomically(file, content);

        assertTrue(fileSystem.exists(file));
        assertFalse(fileSystem.isDirectory(file));
        assertArrayEquals(content, fileSystem.readBytes(file));
        assertEquals(64, fileSystem.sha256(file).length());
        assertNotNull(fileSystem.lastModified(file));
        assertEquals(List.of(file), fileSystem.listFiles(temporaryDirectory));
    }

    @Test
    void stubsCaptureRequestsWithoutStartingExternalTools() {
        ProcessResult processResult = new ProcessResult(0, "", "", Duration.ZERO, false);
        PortTestDoubles.StubProcessRunner processRunner = new PortTestDoubles.StubProcessRunner(processResult);
        ProcessRequest processRequest = new ProcessRequest(List.of("git", "status"), temporaryDirectory,
                null, Duration.ofSeconds(1));
        assertEquals(processResult, processRunner.run(processRequest));
        assertEquals(processRequest, processRunner.lastRequest());

        PortTestDoubles.StubGitPort git = new PortTestDoubles.StubGitPort();
        assertTrue(git.status(temporaryDirectory).repository());
        git.commit(temporaryDirectory, "test");
        assertTrue(git.operations().contains("commit:test"));

        PortTestDoubles.FixedClock clock = new PortTestDoubles.FixedClock(Instant.parse("2026-11-22T12:00:00Z"));
        assertEquals(Instant.parse("2026-11-22T12:00:00Z"), clock.now());
    }
}
