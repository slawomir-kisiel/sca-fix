package pl.sca.fix.maven;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.report.UpdateLevel;

class MavenPluginConfigurationValidatorTest {
    @Test
    void allowsReadOnlyScanWithDefaultProjects() {
        assertDoesNotThrow(() -> new MavenPluginConfigurationValidator().validate(
                new MavenPluginConfiguration(Path.of("checkout"), Path.of("workspaces"), Path.of("poms"),
                        List.of("*"), "file", Path.of("report.json"), null, UpdateLevel.MINOR,
                        "COMMON_SOURCE", "R1d,S10m", false, false, false, null, false, false)));
    }

    @Test
    void rejectsPushWithoutCommitAndLocalChanges() {
        MavenPluginConfiguration config = new MavenPluginConfiguration(Path.of("checkout"), Path.of("workspaces"),
                Path.of("poms"), List.of("*"), null, null, null, UpdateLevel.MINOR, "COMMON_SOURCE",
                "R1d,S10m", true, true, false, "branch", false, false);
        assertThrows(IllegalArgumentException.class, () -> new MavenPluginConfigurationValidator().validate(config));
    }
}
