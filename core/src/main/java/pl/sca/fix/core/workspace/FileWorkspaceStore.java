package pl.sca.fix.core.workspace;

import java.nio.file.Path;
import java.util.Objects;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.WorkspaceStorePort;
import pl.sca.fix.core.pipeline.PipelineConfiguration;

/** File-backed workspace store using the core filesystem and JSON codec ports. */
public final class FileWorkspaceStore implements WorkspaceStorePort {
    private static final String STATE_FILE = "workspace.json";
    private static final String CONFIGURATION_FILE = "config.json";

    private final Path workspaceRoot;
    private final FileSystemPort fileSystem;
    private final WorkspaceStateJsonCodec codec;
    private final PipelineConfigurationJsonCodec configurationCodec;

    public FileWorkspaceStore(Path workspaceRoot, FileSystemPort fileSystem,
                              WorkspaceStateJsonCodec codec) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null")
                .toAbsolutePath().normalize();
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.configurationCodec = new PipelineConfigurationJsonCodec();
    }

    @Override
    public WorkspaceState load(String workspaceId) {
        Path file = stateFile(workspaceId);
        if (!fileSystem.exists(file)) {
            throw new PortException(PortException.Kind.NOT_FOUND, "Workspace state not found: " + file);
        }
        try {
            return codec.read(fileSystem.readBytes(file), file.toString());
        } catch (WorkspaceStateException exception) {
            throw new PortException(PortException.Kind.IO, exception.getMessage(), exception);
        }
    }

    @Override
    public void save(WorkspaceState state) {
        Objects.requireNonNull(state, "state must not be null");
        Path directory = workspaceRoot.resolve(state.workspaceId());
        Path file = directory.resolve(STATE_FILE);
        try {
            fileSystem.createDirectories(directory);
            fileSystem.writeAtomically(file, codec.write(state));
        } catch (PortException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to save workspace state: " + file, exception);
        }
    }

    @Override
    public void saveConfiguration(String workspaceId, PipelineConfiguration configuration) {
        Path directory = workspaceDirectory(workspaceId);
        Path file = directory.resolve(CONFIGURATION_FILE);
        try {
            fileSystem.createDirectories(directory);
            fileSystem.writeAtomically(file, configurationCodec.write(configuration));
        } catch (PortException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to save workspace configuration: " + file, exception);
        }
    }

    @Override
    public PipelineConfiguration loadConfiguration(String workspaceId) {
        Path file = workspaceDirectory(workspaceId).resolve(CONFIGURATION_FILE);
        if (!fileSystem.exists(file)) {
            throw new PortException(PortException.Kind.NOT_FOUND, "Workspace configuration not found: " + file);
        }
        try {
            return configurationCodec.read(fileSystem.readBytes(file), file.toString());
        } catch (WorkspaceStateException exception) {
            throw new PortException(PortException.Kind.IO, exception.getMessage(), exception);
        }
    }

    @Override
    public boolean exists(String workspaceId) {
        return fileSystem.exists(stateFile(workspaceId));
    }

    @Override
    public void delete(String workspaceId) {
        Path directory = workspaceDirectory(workspaceId);
        if (fileSystem.exists(directory) && !fileSystem.isDirectory(directory)) {
            throw new PortException(PortException.Kind.CONFLICT, "Workspace path is not a directory: " + directory);
        }
        fileSystem.deleteTree(directory);
    }

    private Path stateFile(String workspaceId) {
        return workspaceDirectory(workspaceId).resolve(STATE_FILE);
    }

    private Path workspaceDirectory(String workspaceId) {
        if (!WorkspaceState.isValidId(workspaceId)) {
            throw new PortException(PortException.Kind.INVALID_INPUT, "Invalid workspaceId: " + workspaceId);
        }
        Path directory = workspaceRoot.resolve(workspaceId).normalize();
        if (!directory.startsWith(workspaceRoot)) {
            throw new PortException(PortException.Kind.INVALID_INPUT, "Workspace path escapes root: " + workspaceId);
        }
        return directory;
    }
}
