package pl.sca.fix.core.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.port.PortTestDoubles;

class JsonDependencyTreeParserTest {
    @TempDir
    Path directory;

    @Test
    void buildsStableIdsAndReferencesForNestedAndRepeatedArtifacts() throws Exception {
        Path input = write("""
                {
                  "project": {"groupId":"pl.sca", "artifactId":"app", "version":"1.0"},
                  "dependencies": [
                    {"groupId":"com.test", "artifactId":"test", "type":"jar", "version":"1", "scope":"compile",
                     "children":[{"groupId":"org.apache", "artifactId":"io", "version":"2", "scope":"runtime"}]},
                    {"groupId":"com.test", "artifactId":"test", "version":"1", "scope":"test"}
                  ]
                }
                """);

        DependencyTree tree = new JsonDependencyTreeParser().parse(input);

        assertEquals("pl.sca:app:jar:1.0", String.join(":", tree.root().coordinate().groupId(),
                tree.root().coordinate().artifactId(), tree.root().coordinate().type(),
                tree.root().coordinate().version()));
        assertEquals(List.of("0.0"), tree.references().get("org.apache:io"));
        assertEquals(List.of("0", "1"), tree.references().get("com.test:test"));
        assertEquals("0.0", tree.root().children().get(0).children().get(0).nodeId());
    }

    @Test
    void acceptsTopLevelDependenciesAndTextualOptional() throws Exception {
        Path input = write("""
                {"project":{"groupId":"g","artifactId":"a","version":"1"},"dependencies":[
                  {"groupId":"g2","artifactId":"a2","version":"2","optional":"true","classifier":"tests"}
                ]}
                """);
        DependencyTree tree = new JsonDependencyTreeParser().parse(input);
        DependencyTreeNode node = tree.root().children().get(0);
        assertTrue(node.optional());
        assertEquals("tests", node.coordinate().classifier());
    }

    @Test
    void parsesTheNativePluginRootWithChildren() throws Exception {
        Path input = write("""
                {"groupId":"pl.sca","artifactId":"sca-fix-core","version":"0.1.0-SNAPSHOT",
                 "type":"jar","scope":"","classifier":"","optional":"false","children":[
                   {"groupId":"g","artifactId":"a","version":"1","type":"jar","scope":"compile"}
                 ]}
                """);

        DependencyTree tree = new JsonDependencyTreeParser().parse(input);

        assertEquals("pl.sca", tree.root().coordinate().groupId());
        assertEquals("0", tree.root().children().get(0).nodeId());
        assertEquals(List.of("0"), tree.references().get("g:a"));
    }

    @Test
    void rejectsMalformedInput() throws Exception {
        Path input = write("{\"project\":{},\"dependencies\":[{\"groupId\":\"g\"}]}");
        assertThrows(DependencyTreeParseException.class, () -> new JsonDependencyTreeParser().parse(input));
        Path invalid = write("not-json");
        assertThrows(DependencyTreeParseException.class, () -> new JsonDependencyTreeParser().parse(invalid));
    }

    @Test
    void writesTextAndJsonIndexAtomically() throws Exception {
        Path input = write("""
                {"project":{"groupId":"g","artifactId":"a","version":"1"},"dependencies":[
                  {"groupId":"g2","artifactId":"a2","version":"2","scope":"compile"}
                ]}
                """);
        DependencyTree tree = new JsonDependencyTreeParser().parse(input);
        Path output = directory.resolve("tree.tree.txt");
        new DependencyTreeFileWriter(new PortTestDoubles.TempFileSystem()).write(output, tree);
        String result = Files.readString(output);
        assertTrue(result.startsWith("g:a:jar:1\n\tg2:a2:jar:2:compile\n----\n"));
        assertTrue(result.contains("\"g2:a2\""));
        assertTrue(result.contains("\"0\""));
        DependencyTree read = new DependencyTreeFileReader(new PortTestDoubles.TempFileSystem()).read(output);
        assertEquals("g2:a2", read.references().keySet().iterator().next());
        assertEquals("0", read.root().children().get(0).nodeId());
    }

    private Path write(String content) throws Exception {
        Path path = directory.resolve("tree-" + Files.list(directory).count() + ".json");
        return Files.writeString(path, content);
    }
}
