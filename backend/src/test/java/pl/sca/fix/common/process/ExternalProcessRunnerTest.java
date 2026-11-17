package pl.sca.fix.common.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalProcessRunnerTest {

    @Test
    void adaptsCommandsForWindowsCommandShell() {
        String originalOsName = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");

            assertThat(ExternalProcessRunner.adaptCommandForCurrentOs(
                    List.of("mvn", "-f", "C:\\workspace\\project with spaces\\pom.xml", "dependency:tree")))
                    .containsExactly("cmd.exe", "/d", "/s", "/c",
                            "mvn \"-f\" \"C:\\workspace\\project with spaces\\pom.xml\" \"dependency:tree\"");
        } finally {
            if (originalOsName == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", originalOsName);
            }
        }
    }
}
