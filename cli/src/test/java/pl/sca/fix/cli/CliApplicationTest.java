package pl.sca.fix.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CliApplicationTest {
    @Test
    void versionDoesNotBuildOrRunPipeline() {
        assertEquals(0, CliApplication.run(new String[]{"--version"}));
    }

    @Test
    void invalidConfigurationReturnsConfigurationExitCode() {
        assertEquals(2, CliApplication.run(new String[]{"scan", "--report-source", "file"}));
    }
}
