package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.maven.EffectivePom;
import pl.sca.fix.core.maven.EffectivePomJsonCodec;
import pl.sca.fix.core.maven.NioFileSystemAdapter;

class PlanningProjectManifestGeneratorTest {
    @TempDir Path temp;

    @Test
    void generatesManifestFromLocalEffectivePom() throws Exception {
        Path repository = temp.resolve("repository");
        Path pom = repository.resolve("module/pom.xml");
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, "<project/>");
        Path cache = temp.resolve("cache/com.example/app/1.0");
        EffectivePom model = new EffectivePom(new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                "com.example", "app", "1.0", null, List.of(), List.of(),
                List.of(new EffectivePom.PomSource("com.example", "app", "1.0", "module/pom.xml", "hash", Instant.parse("2026-01-01T00:00:00Z"), true)));
        new EffectivePomJsonCodec(new NioFileSystemAdapter()).writeAtomically(cache.resolve("effective-pom.json"), model);
        Path output = temp.resolve("planning-projects.json");

        PlanningProjectManifestGenerator.run(new String[]{"--cache", temp.resolve("cache").toString(),
                "--repository", repository.toString(), "--output", output.toString()});

        List<PlanningProject> projects = new ObjectMapper().findAndRegisterModules()
                .readValue(Files.readAllBytes(output), new com.fasterxml.jackson.core.type.TypeReference<>() { });
        assertEquals(1, projects.size());
        assertEquals(pom.toAbsolutePath().normalize(), projects.get(0).pomPath());
        assertEquals("com.example:app:1.0", projects.get(0).projectId());
        assertEquals(true, projects.get(0).editable());
    }
}
