package pl.sca.fix.integration.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.project.domain.Project;

class MavenProjectRepositoryResolverTest {
    @Test
    void readsRepositoriesAndTheirReleasePoliciesFromEffectivePom(@TempDir Path root) throws Exception {
        Project project = new Project(UUID.randomUUID(), UUID.randomUUID(), "demo", "pom.xml",
                "com.example", "demo", "1.0.0", "jar", null, null, null, "main", "abc1234");
        Path file = root.resolve("com.example/demo/1.0.0/effective-pom.xml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                <project>
                  <repositories>
                    <repository>
                      <id>internal</id>
                      <url>https://repo.example/maven</url>
                      <releases><enabled>true</enabled></releases>
                      <snapshots><enabled>false</enabled></snapshots>
                    </repository>
                    <repository>
                      <id>snapshots</id>
                      <url>https://repo.example/snapshots</url>
                      <snapshots><enabled>true</enabled></snapshots>
                    </repository>
                  </repositories>
                </project>
                """);

        var repositories = new MavenProjectRepositoryResolver(root.toString()).resolve(project);

        assertEquals(2, repositories.size());
        assertEquals("internal", repositories.get(0).getId());
        assertTrue(repositories.get(0).isReleases());
        assertFalse(repositories.get(0).isSnapshots());
        assertEquals("snapshots", repositories.get(1).getId());
        assertTrue(repositories.get(1).isReleases());
        assertTrue(repositories.get(1).isSnapshots());
    }
}
