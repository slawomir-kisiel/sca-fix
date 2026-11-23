package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.maven.NioFileSystemAdapter;

class PomEditorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void changesDirectDependencyAndKeepsSurroundingText() throws Exception {
        Path pom = write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <!-- keep this comment -->
                  <dependencies>
                    <dependency>
                      <groupId>g</groupId>
                      <artifactId>a</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        PomChange change = editor().prepare(new PomEditRequest(pom, "DIRECT_DEPENDENCY", "g", "a",
                "1.0.0", "1.1.0", null, null, null, null, null, Map.of(), null));

        assertTrue(change.changed());
        assertEquals("  ", change.indentation());
        assertTrue(change.changedContent().contains("<version>1.1.0</version>"));
        assertTrue(change.changedContent().contains("<!-- keep this comment -->"));
        assertTrue(change.diff().contains("-"));
        assertEquals(Files.readString(pom), change.originalContent());
    }

    @Test
    void changesOnlyDependencyManagementEntry() throws Exception {
        Path pom = write("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId><version>9</version></dependency></dependencies>
                    <dependencyManagement><dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId><version>1</version></dependency></dependencies></dependencyManagement>
                </project>
                """);

        PomChange change = editor().prepare(new PomEditRequest(pom, "DEPENDENCY_MANAGEMENT", "g", "a",
                "1", "2", null, null, null, null, null, Map.of(), null));

        assertEquals("    ", change.indentation());
        assertTrue(change.changedContent().contains("<version>9</version>"));
        assertTrue(change.changedContent().contains("<version>2</version>"));
    }

    @Test
    void directDependencyDoesNotSelectDependencyManagementSection() throws Exception {
        Path pom = write("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement><dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId><version>1</version></dependency></dependencies></dependencyManagement>
                    <dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId><version>2</version></dependency></dependencies>
                </project>
                """);

        PomChange change = editor().prepare(new PomEditRequest(pom, "DIRECT_DEPENDENCY", "g", "a",
                "2", "3", null, null, null, null, null, Map.of(), null));

        assertTrue(change.changedContent().contains("<version>1</version>"));
        assertTrue(change.changedContent().contains("<version>3</version>"));
    }

    @Test
    void changesPropertyAndIsIdempotent() throws Exception {
        Path pom = write("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <properties><a.version>1.0</a.version></properties>
                </project>
                """);
        PomEditRequest request = new PomEditRequest(pom, "PROPERTY", "g", "a", "1.0", "2.0",
                "a.version", null, null, null, null, Map.of(), null);

        PomChange first = editor().prepare(request);
        Files.writeString(pom, first.changedContent());
        PomChange second = editor().prepare(request);

        assertTrue(first.changed());
        assertTrue(!second.changed());
        assertEquals(first.changedContent(), second.changedContent());
    }

    @Test
    void createsCveOverrideAndUsesExistingPropertiesSection() throws Exception {
        Path pom = write("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <properties>
                        <existing>value</existing>
                    </properties>
                </project>
                """);

        PomChange change = editor().prepare(new PomEditRequest(pom, "CVE_OVERRIDE", "g", "a", null, "2.1",
                null, null, null, null, null, Map.of(), null));

        assertTrue(change.changedContent().contains("<dependencyManagement>"));
        assertTrue(change.changedContent().contains("<cve-override.a.version>2.1</cve-override.a.version>"));
        assertEquals(3, count(change.changedContent(), "cve-override.a.version"));
        assertTrue(change.changedContent().contains("        <existing>value</existing>"));
        assertTrue(change.changedContent().contains("        <cve-override.a.version>2.1</cve-override.a.version>"));
        assertTrue(change.changedContent().contains("    </dependencies>"));
    }

    @Test
    void removesOwnedOverrideButKeepsOtherPropertyUsage() throws Exception {
        Path pom = write("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement><dependencies>
                        <dependency><groupId>g</groupId><artifactId>a</artifactId><version>${cve-override.a.version}</version></dependency>
                    </dependencies></dependencyManagement>
                    <properties><cve-override.a.version>2.1</cve-override.a.version><other>${cve-override.a.version}</other></properties>
                </project>
                """);

        PomChange change = editor().prepare(new PomEditRequest(pom, "REMOVE_CVE_OVERRIDE", "g", "a", "2.1", null,
                "cve-override.a.version", null, null, null, "REMOVE", Map.of(), null));

        assertTrue(!change.changedContent().contains("<dependency><groupId>g</groupId>"));
        assertTrue(change.changedContent().contains("<cve-override.a.version>"));
    }

    @Test
    void detectsTabIndentationForNewSections() throws Exception {
        Path pom = write("""
                <project>
                \t<modelVersion>4.0.0</modelVersion>
                </project>
                """);

        PomChange change = editor().prepare(new PomEditRequest(pom, "CVE_OVERRIDE", "g", "a", null, "1",
                null, null, null, null, null, Map.of(), null));

        assertEquals("\t", change.indentation());
        assertTrue(change.changedContent().contains("\t<dependencyManagement>"));
        assertTrue(change.changedContent().contains("\t\t<dependencies>"), change.changedContent());
    }

    @Test
    void rejectsVersionMismatchAndMissingIndentation() throws Exception {
        Path pom = write("<project>\n  <modelVersion>4.0.0</modelVersion>\n  <dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId><version>1</version></dependency></dependencies>\n</project>\n");
        PomEditException mismatch = assertThrows(PomEditException.class, () -> editor().prepare(new PomEditRequest(pom,
                "DIRECT_DEPENDENCY", "g", "a", "2", "3", null, null, null, null, null, Map.of(), null)));
        assertEquals("CURRENT_VERSION_MISMATCH", mismatch.issue().reason());

        Path noIndent = write("<project></project>");
        PomEditException indentation = assertThrows(PomEditException.class, () -> editor().prepare(new PomEditRequest(noIndent,
                "CVE_OVERRIDE", "g", "a", null, "3", null, null, null, null, null, Map.of(), null)));
        assertEquals("INDENTATION_STYLE_NOT_DETECTED", indentation.issue().reason());
    }

    private PomEditor editor() { return new PomEditor(new NioFileSystemAdapter()); }

    private Path write(String content) throws Exception {
        Path pom = temporaryDirectory.resolve("pom-" + System.nanoTime() + ".xml");
        Files.writeString(pom, content);
        return pom;
    }

    private static int count(String value, String needle) {
        int count = 0, from = 0;
        while ((from = value.indexOf(needle, from)) >= 0) { count++; from += needle.length(); }
        return count;
    }
}
