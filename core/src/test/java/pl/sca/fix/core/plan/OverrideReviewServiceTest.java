package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.maven.EffectivePom;
import pl.sca.fix.core.maven.DependencyTree;
import pl.sca.fix.core.maven.DependencyTreeNode;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.maven.VersionCacheDocument;
import pl.sca.fix.core.model.ArtifactCoordinate;
import pl.sca.fix.core.port.MavenDependencyTreeRequest;
import pl.sca.fix.core.port.MavenEffectivePomRequest;
import pl.sca.fix.core.port.MavenExecutionResult;
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.core.port.ProcessResult;
import pl.sca.fix.core.report.ProjectTree;
import pl.sca.fix.core.report.UpdateLevel;

class OverrideReviewServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsRemovalItemAndRestoresPomAfterRedundantReview() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        String original = """
                <project><groupId>g</groupId><artifactId>app</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>g</groupId><artifactId>a</artifactId>
                    <version>${cve-override.a.version}</version>
                  </dependency></dependencies></dependencyManagement>
                  <properties><cve-override.a.version>1.0.0</cve-override.a.version></properties>
                </project>
                """;
        Files.writeString(pom, original);
        EffectivePom baseline = effective("1.0.0");
        PlanningProject project = new PlanningProject("g:app:${revision}", pom, baseline, true);

        FakeMaven maven = new FakeMaven("2.0.0");
        OverrideReviewResult result = new OverrideReviewService(new NioFileSystemAdapter(),
                maven, temporaryDirectory.resolve("probe"))
                .review(List.of(project), temporaryDirectory, "commit", null);

        assertEquals(OverrideReviewStatus.REDUNDANT, result.entries().get(0).status());
        assertEquals("REMOVE_CVE_OVERRIDE", result.plan().items().get(0).strategy());
        assertEquals(original, Files.readString(pom));
        assertFalse(maven.effectivePomOutput.toString().contains("${revision}"));
        assertTrue(Files.list(temporaryDirectory)
                .noneMatch(path -> path.getFileName().toString().contains("scafix-override-")));
    }

    @Test
    void ignoresDependencyManagementVersionsThatDoNotUseCveOverrideProperty() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        String original = """
                <project><groupId>g</groupId><artifactId>app</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>g</groupId><artifactId>a</artifactId>
                    <version>${regular.a.version}</version>
                  </dependency></dependencies></dependencyManagement>
                  <properties><regular.a.version>1.0.0</regular.a.version></properties>
                </project>
                """;
        Files.writeString(pom, original);
        PlanningProject project = new PlanningProject("g:app:1", pom, effective("1.0.0"), true);
        FakeMaven maven = new FakeMaven("2.0.0");

        OverrideReviewResult result = new OverrideReviewService(new NioFileSystemAdapter(),
                maven, temporaryDirectory.resolve("probe"))
                .review(List.of(project), temporaryDirectory, "commit", null);

        assertTrue(result.entries().isEmpty());
        assertTrue(result.plan().items().isEmpty());
        assertTrue(result.plan().warnings().isEmpty());
        assertTrue(result.plan().errors().isEmpty());
        assertNull(maven.effectivePomOutput);
        assertEquals(original, Files.readString(pom));
    }

    @Test
    void appliesAllAvailableDirectUpdatesBeforeOneEffectivePomProbe() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        String original = """
                <project><groupId>g</groupId><artifactId>app</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>x</groupId><artifactId>b</artifactId><version>1.0.0</version></dependency>
                    <dependency><groupId>x</groupId><artifactId>e</artifactId><version>1.0.0</version></dependency>
                  </dependencies>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>g</groupId><artifactId>d</artifactId>
                    <version>${cve-override.d.version}</version>
                  </dependency></dependencies></dependencyManagement>
                  <properties><cve-override.d.version>2.0.0</cve-override.d.version></properties>
                </project>
                """;
        Files.writeString(pom, original);
        EffectivePom baseline = new EffectivePom(new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                "g", "app", "1", null,
                List.of(new EffectivePom.Dependency("x", "b", "1.0.0", null, null, null, null),
                        new EffectivePom.Dependency("x", "e", "1.0.0", null, null, null, null)),
                List.of(new EffectivePom.Dependency("g", "d", "2.0.0", null, null, null, null)), List.of());
        PlanningProject project = new PlanningProject("g:app:1", pom, baseline, true);
        DependencyTreeNode root = new DependencyTreeNode(null, new ArtifactCoordinate("g", "app", "1"), "", false,
                List.of(new DependencyTreeNode("0", new ArtifactCoordinate("x", "b", "1.0.0"), "compile", false,
                                List.of(new DependencyTreeNode("0.0", new ArtifactCoordinate("g", "d", "2.0.0"), "compile", false, List.of()))),
                        new DependencyTreeNode("1", new ArtifactCoordinate("x", "e", "1.0.0"), "compile", false,
                                List.of(new DependencyTreeNode("1.0", new ArtifactCoordinate("g", "d", "2.0.0"), "compile", false, List.of())))));
        ProjectTree tree = new ProjectTree("g:app:1", pom, new DependencyTree(root, Map.of()));
        Map<String, List<VersionCacheDocument.VersionEntry>> versions = Map.of(
                "x:b", List.of(new VersionCacheDocument.VersionEntry("1.1.0", List.of(), "release")),
                "x:e", List.of(new VersionCacheDocument.VersionEntry("1.2.0", List.of(), "release")));
        FakeMaven maven = new FakeMaven("2.0.0");

        OverrideReviewService service = new OverrideReviewService(new NioFileSystemAdapter(), maven,
                temporaryDirectory.resolve("probe"));
        assertEquals(Set.of("x:b", "x:e"), service.versionKeysForReview(List.of(project), List.of(tree)));
        OverrideReviewResult result = service.review(List.of(project), List.of(tree), versions,
                temporaryDirectory, "commit", UpdateLevel.MINOR);

        assertEquals(OverrideReviewStatus.REDUNDANT, result.entries().get(0).status());
        assertEquals(3, result.plan().items().size());
        assertTrue(result.plan().items().stream().anyMatch(item -> item.artifact().artifactId().equals("b")
                && item.targetVersion().equals("1.1.0")));
        assertTrue(result.plan().items().stream().anyMatch(item -> item.artifact().artifactId().equals("e")
                && item.targetVersion().equals("1.2.0")));
        assertTrue(maven.probePom.contains("<artifactId>b</artifactId><version>1.1.0</version>"));
        assertTrue(maven.probePom.contains("<artifactId>e</artifactId><version>1.2.0</version>"));
        assertEquals(1, maven.effectivePomCalls);
        assertEquals(original, Files.readString(pom));
    }

    private static EffectivePom effective(String version) {
        return new EffectivePom(new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                "g", "app", "1", null, List.of(),
                List.of(new EffectivePom.Dependency("g", "a", version, null, null, null, null)), List.of());
    }

    private static final class FakeMaven implements MavenPort {
        private final String version;
        private Path effectivePomOutput;
        private String probePom;
        private int effectivePomCalls;

        private FakeMaven(String version) { this.version = version; }

        @Override public MavenExecutionResult generateEffectivePom(MavenEffectivePomRequest request) {
            effectivePomOutput = request.outputFile();
            effectivePomCalls++;
            try { probePom = Files.readString(request.pomFile()); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
            String xml = """
                    <project><groupId>g</groupId><artifactId>app</artifactId><version>1</version>
                      <dependencyManagement><dependencies><dependency>
                        <groupId>g</groupId><artifactId>a</artifactId><version>%s</version>
                      </dependency><dependency>
                        <groupId>g</groupId><artifactId>d</artifactId><version>%s</version>
                      </dependency></dependencies></dependencyManagement>
                    </project>
                    """.formatted(version, version);
            Filesystem.write(request.outputFile(), xml);
            return result(request.outputFile());
        }

        @Override public MavenExecutionResult generateDependencyTree(MavenDependencyTreeRequest request) {
            return result(request.outputFile());
        }

        private static MavenExecutionResult result(Path output) {
            return new MavenExecutionResult(new ProcessResult(0, "", "", Duration.ZERO, false), output);
        }

        private static final class Filesystem {
            private static void write(Path path, String value) {
                try { Files.createDirectories(path.getParent()); Files.writeString(path, value); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            }
        }
    }
}
