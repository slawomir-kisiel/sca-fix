package pl.sca.fix.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.config.ScaProperties;

class WorkspaceConfigurationServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void exposesDefaultPathAndDirectoryState() {
        WorkspaceConfigurationService service = serviceWith(tempDirectory.resolve("missing"));

        var config = service.get();

        assertThat(config.rootPath()).isEqualTo(tempDirectory.resolve("missing").toString());
        assertThat(config.effectivePath()).isEqualTo(tempDirectory.resolve("missing").toAbsolutePath());
        assertThat(config.exists()).isFalse();
        assertThat(config.writable()).isFalse();
    }

    @Test
    void createsDefaultWorkspaceDirectory() {
        WorkspaceConfigurationService service = serviceWith(tempDirectory.resolve("work"));

        var config = service.ensureDirectory();

        assertThat(Files.isDirectory(config.effectivePath())).isTrue();
        assertThat(config.writable()).isTrue();
    }

    @Test
    void rejectsMissingDirectoryOnUpdate() {
        WorkspaceConfigurationService service = serviceWith(tempDirectory.resolve("work"));

        assertThatThrownBy(() -> service.update(tempDirectory.resolve("does-not-exist").toString()))
                .isInstanceOf(WorkspacePathException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void updatesExistingWritableDirectory() {
        Path target = tempDirectory.resolve("checkout-root");
        assertThat(target.toFile().mkdirs()).isTrue();
        WorkspaceConfigurationService service = serviceWith(tempDirectory.resolve("work"));

        var config = service.update(target.toString());

        assertThat(config.effectivePath()).isEqualTo(target.toAbsolutePath());
        assertThat(config.exists()).isTrue();
        assertThat(config.writable()).isTrue();
    }

    private WorkspaceConfigurationService serviceWith(Path configuredRoot) {
        return new WorkspaceConfigurationService(new ScaProperties(
                new ScaProperties.Workspace(configuredRoot),
                new ScaProperties.Jobs("30d")),
                tempDirectory.resolve("application-data/workspace.properties"));
    }
}
