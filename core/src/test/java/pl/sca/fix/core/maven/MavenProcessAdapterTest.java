package pl.sca.fix.core.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.port.MavenDependencyTreeRequest;
import pl.sca.fix.core.port.MavenEffectivePomRequest;
import pl.sca.fix.core.port.MavenOwaspReportRequest;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.PortTestDoubles;
import pl.sca.fix.core.port.ProcessResult;

class MavenProcessAdapterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsPinnedEffectivePomCommandWithVerboseAndNonRecursiveMode() throws Exception {
        Path pom = temporaryDirectory.resolve("project with spaces/pom.xml");
        Path output = temporaryDirectory.resolve("cache/effective-pom.xml");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "<project/>");
        PortTestDoubles.StubProcessRunner runner = new PortTestDoubles.StubProcessRunner(success());
        MavenProcessAdapter adapter = new MavenProcessAdapter(runner, new PortTestDoubles.TempFileSystem(),
                new MavenToolConfiguration("mvn", false, "3.5.1", "3.8.1", Duration.ofSeconds(5)));

        adapter.generateEffectivePom(new MavenEffectivePomRequest(pom, output));

        assertEquals(List.of("mvn", "-N", "-f", pom.toAbsolutePath().normalize().toString(),
                "org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom", "-Dverbose",
                "-Doutput=" + output.toAbsolutePath().normalize()), runner.lastRequest().command());
        assertEquals(pom.toAbsolutePath().normalize().getParent(), runner.lastRequest().workingDirectory());
    }

    @Test
    void buildsJsonDependencyTreeCommandAndWindowsWrapper() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        Path output = temporaryDirectory.resolve("tree.json");
        Files.writeString(output, "{}");
        PortTestDoubles.StubProcessRunner runner = new PortTestDoubles.StubProcessRunner(success());
        MavenProcessAdapter adapter = new MavenProcessAdapter(runner, new PortTestDoubles.TempFileSystem(),
                new MavenToolConfiguration("mvn", true, "3.5.1", "3.8.1", Duration.ofSeconds(5)));

        adapter.generateDependencyTree(new MavenDependencyTreeRequest(pom, output, null, false));

        assertEquals("cmd.exe", runner.lastRequest().command().get(0));
        assertEquals("/c", runner.lastRequest().command().get(1));
        assertTrue(runner.lastRequest().command().contains("-DoutputType=json"));
        assertTrue(runner.lastRequest().command().contains("-Dverbose"));
        assertTrue(runner.lastRequest().command().contains(
                "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree"));
    }

    @Test
    void buildsOwaspAggregateCommandWithoutExposingApiKeyInDisplayCommand() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        Path outputDirectory = temporaryDirectory.resolve("report");
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("dependency-check-report.json"), "{}");
        PortTestDoubles.StubProcessRunner runner = new PortTestDoubles.StubProcessRunner(success());
        MavenProcessAdapter adapter = new MavenProcessAdapter(runner, new PortTestDoubles.TempFileSystem(),
                MavenToolConfiguration.defaults(false));

        adapter.generateOwaspReport(new MavenOwaspReportRequest(pom, outputDirectory, "12.1.9",
                "nvd", "ossindex", "secret-value"));

        assertTrue(runner.lastRequest().command().contains(
                "org.owasp:dependency-check-maven:12.1.9:aggregate"));
        assertTrue(runner.lastRequest().command().contains("-Dformat=json"));
        assertTrue(runner.lastRequest().command().contains(
                "-Dodc.outputDirectory=" + outputDirectory.toAbsolutePath().normalize()));
        assertTrue(runner.lastRequest().command().contains("-DnvdApiKey=secret-value"));
    }

    @Test
    void executesDisplayAncestorsAsSeparateGoalOnlyForSupportedPluginVersion() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        Path output = temporaryDirectory.resolve("tree.json");
        Files.writeString(output, "{}");
        PortTestDoubles.StubProcessRunner runner = new PortTestDoubles.StubProcessRunner(List.of(success(), success()));
        MavenProcessAdapter adapter = new MavenProcessAdapter(runner, new PortTestDoubles.TempFileSystem(),
                MavenToolConfiguration.defaults(false));

        adapter.generateDependencyTree(new MavenDependencyTreeRequest(pom, output, "3.8.1", true));

        assertEquals(2, runner.requests().size());
        assertTrue(runner.requests().get(1).command().contains(
                "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:display-ancestors"));
    }

    @Test
    void rejectsUnsupportedAncestorsGoalAndPropagatesProcessFailures() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        Path output = temporaryDirectory.resolve("tree.json");
        Files.writeString(output, "{}");
        PortTestDoubles.StubProcessRunner runner = new PortTestDoubles.StubProcessRunner(success());
        MavenProcessAdapter adapter = new MavenProcessAdapter(runner, new PortTestDoubles.TempFileSystem(),
                MavenToolConfiguration.defaults(false));

        PortException unsupported = assertThrows(PortException.class, () -> adapter.generateDependencyTree(
                new MavenDependencyTreeRequest(pom, output, "2.8", true)));
        assertEquals(PortException.Kind.UNSUPPORTED, unsupported.kind());

        PortTestDoubles.StubProcessRunner failedRunner = new PortTestDoubles.StubProcessRunner(
                new ProcessResult(1, "out", "failure", Duration.ofSeconds(1), false));
        MavenProcessAdapter failedAdapter = new MavenProcessAdapter(failedRunner,
                new PortTestDoubles.TempFileSystem(), MavenToolConfiguration.defaults(false));
        PortException failed = assertThrows(PortException.class, () -> failedAdapter.generateDependencyTree(
                new MavenDependencyTreeRequest(pom, output, null, false)));
        assertEquals(PortException.Kind.PROCESS_FAILED, failed.kind());
    }

    @Test
    void rejectsMissingOutputAfterSuccessfulProcess() {
        Path pom = temporaryDirectory.resolve("pom.xml");
        Path output = temporaryDirectory.resolve("missing/tree.json");
        MavenProcessAdapter adapter = new MavenProcessAdapter(new PortTestDoubles.StubProcessRunner(success()),
                new PortTestDoubles.TempFileSystem(), MavenToolConfiguration.defaults(false));

        PortException exception = assertThrows(PortException.class, () -> adapter.generateDependencyTree(
                new MavenDependencyTreeRequest(pom, output, null, false)));
        assertEquals(PortException.Kind.IO, exception.kind());
    }

    private static ProcessResult success() {
        return new ProcessResult(0, "stdout", "", Duration.ofMillis(10), false);
    }
}
