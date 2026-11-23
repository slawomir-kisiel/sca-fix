package pl.sca.fix.core.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.MavenEffectivePomRequest;
import pl.sca.fix.core.port.MavenExecutionResult;
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.core.port.ProcessResult;

class EffectivePomTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesVerboseDependenciesAndBuildsRuntimeIndex() throws Exception {
        Path file = temporaryDirectory.resolve("effective-pom.xml");
        Files.writeString(file, """
                <project>
                  <groupId>com.test</groupId><artifactId>app</artifactId><version>1.0</version>
                  <parent><groupId>com.test</groupId><artifactId>parent</artifactId><version>2.0</version></parent>
                  <repositories><repository><id>project-repo</id><url>https://repo.example/maven</url>
                    <releases><enabled>true</enabled></releases><snapshots><enabled>false</enabled></snapshots>
                  </repository></repositories>
                  <dependencies>
                    <dependency>
                      <groupId>org.example</groupId><artifactId>lib</artifactId>
                      <version>1.2</version><!-- com.test:app:1.0, line 12 -->
                      <scope>test</scope><type>jar</type><classifier>tests</classifier>
                    </dependency>
                  </dependencies>
                  <dependencyManagement><dependencies>
                    <dependency>
                      <groupId>org.example</groupId><artifactId>lib</artifactId>
                      <version>1.3</version>
                    </dependency>
                  </dependencies></dependencyManagement>
                </project>
                """);

        FileSystemPort fs = new pl.sca.fix.core.maven.NioFileSystemAdapter();
        EffectivePom model = new EffectivePomParser(fs).parseModel(file,
                new EffectivePom.Metadata("hash", fs.lastModified(file)), java.util.List.of());

        assertEquals("com.test", model.groupId());
        assertEquals("com.test:parent:2.0", model.parent().groupId() + ":"
                + model.parent().artifactId() + ":" + model.parent().version());
        assertEquals(2, model.dependencyIndex().get("org.example:lib").size());
        assertEquals("com.test:app:1.0", model.dependencies().get(0).versionLocation().artifact());
        assertEquals(12, model.dependencies().get(0).versionLocation().line());
        assertEquals("test", model.dependencies().get(0).scope());
        assertEquals("tests", model.dependencies().get(0).classifier());
        assertEquals("dependencyManagement", model.dependencyIndex().get("org.example:lib")
                .get(1).section());
        assertEquals("https://repo.example/maven", model.repositories().get(0).url());
    }

    @Test
    void readsMavenLocationCommentSeparatedByWhitespace() throws Exception {
        Path file = temporaryDirectory.resolve("verbose-effective-pom.xml");
        Files.writeString(file, """
                <project>
                  <groupId>com.test</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>2.0</version>  <!-- com.test:parent:1.0, line 42 -->
                    </dependency>
                  </dependencies>
                </project>
                """);

        EffectivePom model = new EffectivePomParser(new NioFileSystemAdapter()).parseModel(file,
                new EffectivePom.Metadata("hash", java.time.Instant.now()), java.util.List.of());

        assertEquals("com.test:parent:1.0", model.dependencies().get(0).versionLocation().artifact());
        assertEquals(42, model.dependencies().get(0).versionLocation().line());
    }

    @Test
    void rejectsDoctypeAndExternalEntities() throws Exception {
        Path file = temporaryDirectory.resolve("malicious.xml");
        Files.writeString(file, """
                <!DOCTYPE project [ <!ENTITY secret SYSTEM "file:///etc/passwd"> ]>
                <project><groupId>com.test</groupId><artifactId>app</artifactId>
                <version>&secret;</version></project>
                """);

        assertThrows(EffectivePomParseException.class,
                () -> new EffectivePomParser(new NioFileSystemAdapter()).parse(file));
    }

    @Test
    void usesCacheUntilProjectOrLocalParentChanges() throws Exception {
        Path repository = temporaryDirectory.resolve("repo");
        Path child = repository.resolve("app/pom.xml");
        Path parent = repository.resolve("pom.xml");
        Files.createDirectories(child.getParent());
        Files.writeString(parent, """
                <project><groupId>com.test</groupId><artifactId>parent</artifactId><version>1.0</version></project>
                """);
        Files.writeString(child, """
                <project><groupId>com.test</groupId><artifactId>app</artifactId><version>1.0</version>
                  <parent><groupId>com.test</groupId><artifactId>parent</artifactId><version>1.0</version>
                    <relativePath>../pom.xml</relativePath></parent>
                </project>
                """);

        AtomicInteger invocations = new AtomicInteger();
        MavenPort maven = new FakeMaven(invocations);
        EffectivePomCache cache = new EffectivePomCache(new NioFileSystemAdapter(), maven);
        EffectivePomCacheRequest request = new EffectivePomCacheRequest(repository, child,
                temporaryDirectory.resolve("cache"), false);

        EffectivePomCache.Loaded first = cache.load(request);
        EffectivePomCache.Loaded second = cache.load(request);
        assertFalse(first.fromCache());
        assertTrue(second.fromCache());
        assertEquals(1, invocations.get());
        assertEquals(2, second.model().pomChain().size());
        assertTrue(Files.exists(temporaryDirectory.resolve("cache/com.test/app/1.0/effective-pom.json")));

        Files.writeString(parent, "<project><groupId>com.test</groupId><artifactId>parent</artifactId><version>1.0</version><!-- changed --></project>");
        EffectivePomCache.Loaded afterParentChange = cache.load(request);
        assertFalse(afterParentChange.fromCache());
        assertEquals(2, invocations.get());

        Files.writeString(child, "<project><groupId>com.test</groupId><artifactId>app</artifactId><version>1.0</version></project>");
        EffectivePomCache.Loaded afterChildChange = cache.load(request);
        assertFalse(afterChildChange.fromCache());
        assertEquals(3, invocations.get());
    }

    @Test
    void reusesCacheWhenCoordinatesAreInheritedFromParent() throws Exception {
        Path repository = temporaryDirectory.resolve("repo");
        Path child = repository.resolve("module/pom.xml");
        Files.createDirectories(child.getParent());
        Files.writeString(repository.resolve("pom.xml"),
                "<project><groupId>com.test</groupId><artifactId>parent</artifactId><version>1.0</version></project>");
        Files.writeString(child, """
                <project><artifactId>module</artifactId>
                  <parent><groupId>com.test</groupId><artifactId>parent</artifactId><version>1.0</version>
                    <relativePath>../pom.xml</relativePath></parent>
                </project>
                """);
        AtomicInteger invocations = new AtomicInteger();
        EffectivePomCache cache = new EffectivePomCache(new NioFileSystemAdapter(), new FakeMaven(invocations));
        EffectivePomCacheRequest request = new EffectivePomCacheRequest(repository, child,
                temporaryDirectory.resolve("cache"), false);

        assertFalse(cache.load(request).fromCache());
        assertTrue(cache.load(request).fromCache());
        assertEquals(1, invocations.get());
    }

    private static final class FakeMaven implements MavenPort {
        private final AtomicInteger invocations;

        private FakeMaven(AtomicInteger invocations) {
            this.invocations = invocations;
        }

        @Override
        public MavenExecutionResult generateEffectivePom(MavenEffectivePomRequest request) {
            invocations.incrementAndGet();
            try {
                Files.createDirectories(request.outputFile().toAbsolutePath().getParent());
                Files.writeString(request.outputFile(), """
                        <project>
                          <groupId>com.test</groupId><artifactId>app</artifactId><version>1.0</version>
                          <dependencies><dependency><groupId>org.example</groupId><artifactId>lib</artifactId>
                            <version>1.2</version><!-- com.test:app:1.0, line 5 --></dependency></dependencies>
                        </project>
                        """);
                return new MavenExecutionResult(new ProcessResult(0, "", "", Duration.ZERO, false), request.outputFile());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public MavenExecutionResult generateDependencyTree(pl.sca.fix.core.port.MavenDependencyTreeRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
