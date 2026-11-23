package pl.sca.fix.core.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.port.PortTestDoubles;

class ProjectDiscoveryServiceTest {
    @TempDir
    Path repository;

    @Test
    void discoversSingleProjectAndIgnoresUntrackedPom() throws Exception {
        write("pom.xml", pom("com.test", "root", "1.0", null, null));
        write("ignored/pom.xml", pom("com.test", "ignored", "1.0", null, null));
        PortTestDoubles.StubGitPort git = git("pom.xml");

        ProjectDiscoveryResult result = service(git).discover(ProjectDiscoveryRequest.cli(repository, List.of()));

        assertEquals(List.of(Path.of("pom.xml")), result.projects().stream().map(project -> project.pomPath()).toList());
    }

    @Test
    void resolvesNestedModulesAndMavenPluginDefaultScope() throws Exception {
        write("pom.xml", pomWithModules("com.test", "root", "1.0", "module-a"));
        write("module-a/pom.xml", pomWithModules("com.test", "module-a", "1.0", "nested/module-b"));
        write("module-a/nested/module-b/pom.xml", pom("com.test", "module-b", "1.0", null, null));
        PortTestDoubles.StubGitPort git = git("pom.xml", "module-a/pom.xml", "module-a/nested/module-b/pom.xml");

        ProjectDiscoveryResult result = service(git).discover(
                ProjectDiscoveryRequest.mavenPlugin(repository, "com.test:root"));

        assertEquals(List.of(Path.of("module-a/nested/module-b/pom.xml"), Path.of("module-a/pom.xml"),
                Path.of("pom.xml")), result.projects().stream().map(project -> project.pomPath()).toList());
        assertEquals(List.of(Path.of("module-a/pom.xml")), result.projects().get(2).modulePomPaths());
        assertEquals(List.of(Path.of("module-a/nested/module-b/pom.xml")), result.projects().get(1).modulePomPaths());
    }

    @Test
    void inheritsMissingCoordinatesFromLocalParent() throws Exception {
        write("pom.xml", pom("com.test", "parent", "1.0", null, null));
        write("child/pom.xml", """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>com.test</groupId><artifactId>parent</artifactId><version>1.0</version></parent>
                  <artifactId>child</artifactId>
                </project>
                """);
        PortTestDoubles.StubGitPort git = git("pom.xml", "child/pom.xml");

        ProjectDiscoveryResult result = service(git).discover(ProjectDiscoveryRequest.cli(repository, List.of("com.test:child")));

        assertEquals("com.test", result.projects().get(0).coordinate().groupId());
        assertEquals("1.0", result.projects().get(0).coordinate().version());
        assertEquals("com.test:parent:1.0", result.projects().get(0).parent().gav());
    }

    @Test
    void supportsExactSelectorsWildcardAndCommaSeparatedValues() throws Exception {
        write("a/pom.xml", pom("com.test", "a", "1.0", null, null));
        write("b/pom.xml", pom("com.test", "b", "1.0", null, null));
        write("other/pom.xml", pom("org.other", "other", "1.0", null, null));
        PortTestDoubles.StubGitPort git = git("a/pom.xml", "b/pom.xml", "other/pom.xml");

        assertEquals(2, service(git).discover(ProjectDiscoveryRequest.cli(repository, List.of("com.test:*"))).projects().size());
        assertEquals(2, service(git).discover(ProjectDiscoveryRequest.cli(repository, List.of("com.test:a,com.test:b"))).projects().size());
        assertEquals(1, service(git).discover(ProjectDiscoveryRequest.cli(repository, List.of("org.other:other:1.0"))).projects().size());
    }

    @Test
    void treatsDifferentTrackedAliasesOfOnePomAsOneProject() throws Exception {
        write("a/pom.xml", pom("com.test", "same", "1.0", null, null));
        Files.createDirectories(repository.resolve("b"));
        Files.createLink(repository.resolve("b/pom.xml"), repository.resolve("a/pom.xml"));
        PortTestDoubles.StubGitPort git = git("a/pom.xml", "b/pom.xml");

        ProjectDiscoveryResult result = service(git).discover(ProjectDiscoveryRequest.cli(repository, List.of("*")));

        assertEquals(1, result.projects().size());
        assertEquals(Path.of("a/pom.xml"), result.projects().get(0).pomPath());
    }

    @Test
    void rejectsAmbiguousCoordinatesMissingModulesCyclesAndMalformedPom() throws Exception {
        write("a/pom.xml", pom("com.test", "same", "1.0", null, null));
        write("b/pom.xml", pom("com.test", "same", "2.0", null, null));
        PortTestDoubles.StubGitPort ambiguous = git("a/pom.xml", "b/pom.xml");
        assertThrows(ProjectDiscoveryException.class,
                () -> service(ambiguous).discover(ProjectDiscoveryRequest.cli(repository, List.of("com.test:*"))));

        write("root/pom.xml", pomWithModules("org.test", "root", "1.0", "missing"));
        assertThrows(ProjectDiscoveryException.class,
                () -> service(git("root/pom.xml")).discover(ProjectDiscoveryRequest.mavenPlugin(repository, "org.test:root")));

        write("cycle-a/pom.xml", pomWithModules("org.test", "cycle-a", "1.0", "../cycle-b"));
        write("cycle-b/pom.xml", pomWithModules("org.test", "cycle-b", "1.0", "../cycle-a"));
        assertThrows(ProjectDiscoveryException.class,
                () -> service(git("cycle-a/pom.xml", "cycle-b/pom.xml"))
                        .discover(ProjectDiscoveryRequest.mavenPlugin(repository, "org.test:cycle-a")));

        write("broken/pom.xml", "<project>");
        assertThrows(PomParseException.class,
                () -> service(git("broken/pom.xml")).discover(ProjectDiscoveryRequest.cli(repository, List.of("*"))));
    }

    private ProjectDiscoveryService service(PortTestDoubles.StubGitPort git) {
        return new ProjectDiscoveryService(git, new PortTestDoubles.TempFileSystem());
    }

    private PortTestDoubles.StubGitPort git(String... files) {
        PortTestDoubles.StubGitPort git = new PortTestDoubles.StubGitPort();
        git.trackedFiles(List.of(files).stream().map(Path::of).toList());
        return git;
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = repository.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static String pom(String groupId, String artifactId, String version,
                              String parent, String modules) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>" + groupId + "</groupId>"
                + "<artifactId>" + artifactId + "</artifactId><version>" + version + "</version></project>";
    }

    private static String pomWithModules(String groupId, String artifactId, String version, String module) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>" + groupId + "</groupId>"
                + "<artifactId>" + artifactId + "</artifactId><version>" + version + "</version>"
                + "<modules><module>" + module + "</module></modules></project>";
    }
}
