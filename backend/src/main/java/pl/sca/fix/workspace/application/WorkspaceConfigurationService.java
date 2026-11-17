package pl.sca.fix.workspace.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.sca.fix.config.ScaProperties;
import pl.sca.fix.workspace.domain.WorkspaceConfig;

@Service
public class WorkspaceConfigurationService {
    public static final String DEFAULT_ROOT = "./work";
    private static final String ROOT_PROPERTY = "rootPath";

    private final Path configurationFile;
    private Path rootPath;

    @Autowired
    public WorkspaceConfigurationService(ScaProperties properties) {
        this(properties, Path.of("./data/workspace.properties"));
    }

    WorkspaceConfigurationService(ScaProperties properties, Path configurationFile) {
        this.configurationFile = configurationFile.toAbsolutePath().normalize();
        this.rootPath = loadConfiguredPath(properties.workspace().root());
    }

    public synchronized WorkspaceConfig get() {
        Path effectivePath = effectivePath(rootPath);
        return new WorkspaceConfig(rootPath.toString(), effectivePath,
                Files.isDirectory(effectivePath), isWritableDirectory(effectivePath),
                Path.of(DEFAULT_ROOT).normalize().equals(rootPath));
    }

    public synchronized WorkspaceConfig update(String requestedRootPath) {
        if (requestedRootPath == null || requestedRootPath.isBlank()) {
            throw new WorkspacePathException("WORKSPACE_PATH_REQUIRED", "Workspace path is required");
        }
        Path candidate = Path.of(requestedRootPath.trim()).normalize();
        Path effective = effectivePath(candidate);
        if (!Files.isDirectory(effective)) {
            throw new WorkspacePathException("WORKSPACE_PATH_NOT_FOUND",
                    "Workspace directory does not exist: " + effective);
        }
        if (!isWritableDirectory(effective)) {
            throw new WorkspacePathException("WORKSPACE_PATH_NOT_WRITABLE",
                    "Workspace directory is not writable: " + effective);
        }
        rootPath = candidate;
        persist(candidate);
        return get();
    }

    public synchronized WorkspaceConfig ensureDirectory() {
        Path effective = effectivePath(rootPath);
        try {
            Files.createDirectories(effective);
        } catch (IOException exception) {
            throw new WorkspacePathException("WORKSPACE_DIRECTORY_CREATE_FAILED",
                    "Unable to create workspace directory: " + effective, exception);
        }
        if (!isWritableDirectory(effective)) {
            throw new WorkspacePathException("WORKSPACE_PATH_NOT_WRITABLE",
                    "Workspace directory is not writable: " + effective);
        }
        return get();
    }

    public synchronized Path resolvePath(String requestedPath) {
        Path configured = requestedPath == null || requestedPath.isBlank()
                ? effectivePath(rootPath)
                : Path.of(requestedPath.trim());
        return configured.isAbsolute() ? configured.normalize() : effectivePath(rootPath).resolve(configured).normalize();
    }

    private Path loadConfiguredPath(Path configuredPath) {
        if (Files.isRegularFile(configurationFile)) {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(configurationFile)) {
                properties.load(input);
                String saved = properties.getProperty(ROOT_PROPERTY);
                if (saved != null && !saved.isBlank()) {
                    return Path.of(saved).normalize();
                }
            } catch (IOException | RuntimeException ignored) {
                // A broken local preference must not prevent the application from starting.
            }
        }
        return configuredPath == null ? Path.of(DEFAULT_ROOT) : configuredPath.normalize();
    }

    private void persist(Path value) {
        try {
            Files.createDirectories(configurationFile.getParent());
            Properties properties = new Properties();
            properties.setProperty(ROOT_PROPERTY, value.toString());
            try (var output = Files.newOutputStream(configurationFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(output, "SCA-fix-tool workspace configuration");
            }
        } catch (IOException exception) {
            throw new WorkspacePathException("WORKSPACE_CONFIGURATION_SAVE_FAILED",
                    "Unable to save workspace configuration", exception);
        }
    }

    private Path effectivePath(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private boolean isWritableDirectory(Path path) {
        if (!Files.isDirectory(path) || !Files.isWritable(path)) {
            return false;
        }
        Path probe = path.resolve(".sca-fix-write-test");
        try {
            Files.createFile(probe);
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
