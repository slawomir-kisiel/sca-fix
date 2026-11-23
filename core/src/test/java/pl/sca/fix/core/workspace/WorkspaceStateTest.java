package pl.sca.fix.core.workspace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import pl.sca.fix.core.pipeline.PipelineConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.PortTestDoubles;

class WorkspaceStateTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void codecRoundTripIsDeterministic() {
        WorkspaceState state = sampleState();
        WorkspaceStateJsonCodec codec = new WorkspaceStateJsonCodec();

        byte[] first = codec.write(state);
        byte[] second = codec.write(state);

        assertArrayEquals(first, second);
        assertEquals(state, codec.read(first, "memory"));
    }

    @Test
    void codecRejectsCorruptedAndUnsupportedDocuments() {
        WorkspaceStateJsonCodec codec = new WorkspaceStateJsonCodec();

        assertThrows(WorkspaceStateException.class, () -> codec.read("{broken".getBytes(), "broken.json"));
        assertThrows(WorkspaceStateException.class, () -> codec.read(
                codec.write(new WorkspaceState(2, "ws-1", Instant.parse("2026-11-22T12:00:00Z"),
                        Instant.parse("2026-11-22T12:00:00Z"), WorkspaceState.Status.NEW,
                        WorkspaceState.Stage.DISCOVERY, sampleState().configuration(), List.of(), List.of(),
                        sampleState().artifacts(), List.of(), List.of(), List.of())), "future.json"));
    }

    @Test
    void migratesLegacyStringWarningsToReasonAndMessage() {
        String json = "{\"schemaVersion\":1,\"workspaceId\":\"ws-legacy\","
                + "\"createdAt\":\"2026-11-22T12:00:00Z\",\"updatedAt\":\"2026-11-22T12:00:00Z\","
                + "\"status\":\"NEW\",\"currentStage\":\"DISCOVERY\","
                + "\"configuration\":{\"repository\":\"C:/checkout\",\"poms\":\"C:/poms\",\"projects\":[],\"ignoreLocalChanges\":false,\"updateLevel\":\"MINOR\"},"
                + "\"repositories\":[],\"projects\":[],\"artifacts\":{\"poms\":[]},\"stages\":[],"
                + "\"warnings\":[\"CORRELATION_WARNING:missing dependency\"],\"errors\":[]}";

        WorkspaceState state = new WorkspaceStateJsonCodec().read(json.getBytes(), "legacy.json");

        assertEquals("CORRELATION_WARNING", state.warnings().get(0).reason());
        assertEquals("missing dependency", state.warnings().get(0).message());
    }

    @Test
    void fileStorePersistsStateAndReportsMissingWorkspacePath() {
        PortTestDoubles.TempFileSystem fileSystem = new PortTestDoubles.TempFileSystem();
        FileWorkspaceStore store = new FileWorkspaceStore(temporaryDirectory, fileSystem,
                new WorkspaceStateJsonCodec());
        WorkspaceState state = sampleState();

        store.save(state);

        assertTrue(store.exists(state.workspaceId()));
        assertEquals(state, store.load(state.workspaceId()));
        store.delete(state.workspaceId());
        assertTrue(!store.exists(state.workspaceId()));
        PortException missing = assertThrows(PortException.class, () -> store.load("missing"));
        assertEquals(PortException.Kind.NOT_FOUND, missing.kind());
        assertTrue(missing.getMessage().contains("workspace.json"));
    }

    @Test
    void failedAtomicSaveDoesNotReplaceExistingState() {
        PortTestDoubles.TempFileSystem fileSystem = new PortTestDoubles.TempFileSystem();
        FileWorkspaceStore store = new FileWorkspaceStore(temporaryDirectory, fileSystem,
                new WorkspaceStateJsonCodec());
        WorkspaceState original = sampleState();
        store.save(original);
        PipelineConfiguration configuration = new PipelineConfiguration(1, "C:/checkout", "workspaces", "poms",
                List.of("*"), pl.sca.fix.core.report.UpdateLevel.MINOR, false, true, false, false, false, false);
        store.saveConfiguration(original.workspaceId(), configuration);
        assertEquals(configuration, store.loadConfiguration(original.workspaceId()));

        FileWorkspaceStore failingStore = new FileWorkspaceStore(temporaryDirectory,
                new PortTestDoubles.FailingAtomicFileSystem(), new WorkspaceStateJsonCodec());
        WorkspaceState changed = new WorkspaceState(original.schemaVersion(), original.workspaceId(),
                original.createdAt(), Instant.parse("2026-11-22T12:03:10Z"), WorkspaceState.Status.FAILED,
                WorkspaceState.Stage.MAVEN_ANALYSIS, original.configuration(), original.repositories(),
                original.projects(), original.artifacts(), original.stages(), original.warnings(), original.errors());

        assertThrows(PortException.class, () -> failingStore.save(changed));
        assertEquals(original, store.load(original.workspaceId()));
    }

    @Test
    void storeRejectsUnsafeWorkspaceIdentifiers() {
        FileWorkspaceStore store = new FileWorkspaceStore(temporaryDirectory,
                new PortTestDoubles.TempFileSystem(), new WorkspaceStateJsonCodec());

        PortException exception = assertThrows(PortException.class, () -> store.exists("../outside"));

        assertEquals(PortException.Kind.INVALID_INPUT, exception.kind());
    }

    private static WorkspaceState sampleState() {
        WorkspaceState.Configuration configuration = new WorkspaceState.Configuration(
                "C:/checkout", "C:/target/scafix/poms", List.of("com.test:*"), false, "MINOR");
        WorkspaceState.Artifacts artifacts = new WorkspaceState.Artifacts(
                "report.json", "plan.json", List.of(new WorkspaceState.PomArtifact(
                        "com.test:test:12.3", "C:/target/scafix/poms/com/test/test/12.3/effective-pom.json",
                        "C:/target/scafix/poms/com/test/test/12.3/dependency-tree.json")));
        WorkspaceState.StageState stage = new WorkspaceState.StageState(
                "DISCOVERY", WorkspaceState.StageStatus.COMPLETED,
                Instant.parse("2026-11-22T12:00:01Z"), Instant.parse("2026-11-22T12:00:02Z"), 1, List.of(), null, null);
        return new WorkspaceState(1, "ws-1", Instant.parse("2026-11-22T12:00:00Z"),
                Instant.parse("2026-11-22T12:00:02Z"), WorkspaceState.Status.RUNNING,
                WorkspaceState.Stage.MAVEN_ANALYSIS, configuration,
                List.of(new WorkspaceState.Repository("C:/checkout", null, "abc123")),
                List.of(new WorkspaceState.Project("com.test", "test", "12.3", "pom.xml", true)),
                artifacts, List.of(stage), List.of(new WorkspaceState.Issue("WARNING", "warning", "DISCOVERY",
                        Instant.parse("2026-11-22T12:00:03Z"))),
                 List.of(new WorkspaceState.Issue("TEST", "example", "DISCOVERY", Instant.parse("2026-11-22T12:00:03Z"))));
    }
}
