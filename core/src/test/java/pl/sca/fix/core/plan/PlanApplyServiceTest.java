package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitStatus;
import pl.sca.fix.core.report.UpdateLevel;

class PlanApplyServiceTest {
    @TempDir
    Path repository;

    @Test
    void continuesWithRemainingItemsAfterOneItemFails() throws Exception {
        Path valid = write("valid/pom.xml", pom("a", "1.0"));
        Path invalid = write("invalid/pom.xml", pom("other", "1.0"));
        PlanItem first = item("one", "valid/pom.xml", "a", "1.0", "2.0", valid);
        PlanItem second = item("two", "invalid/pom.xml", "missing", "1.0", "2.0", invalid);
        PlanApplyResult result = service().apply(request(List.of(first, second), false, false));

        assertEquals("APPLIED", result.plan().items().get(0).status());
        assertEquals("FAILED", result.plan().items().get(1).status());
        assertTrue(result.plan().items().get(1).errorMessage() != null);
        assertEquals(1, result.summary().applied());
        assertEquals(1, result.summary().failed());
        assertTrue(Files.readString(valid).contains("<version>2.0</version>"));
    }

    @Test
    void rejectsDirtyRepositoryUnlessIgnoreLocalChangesIsEnabled() throws Exception {
        Path pom = write("pom.xml", pom("a", "1.0"));
        PlanItem item = item("one", "pom.xml", "a", "1.0", "2.0", pom);
        PlanApplyException exception = assertThrows(PlanApplyException.class,
                () -> service(false).apply(request(List.of(item), false, false)));
        assertEquals("WORKTREE_DIRTY", exception.issue().reason());

        PlanApplyResult result = service(false).apply(request(List.of(item), true, true));
        assertTrue(result.summary().pushBlocked());
        assertEquals("APPLIED", result.plan().items().get(0).status());
    }

    @Test
    void retriesFailedPlanWithoutRequiringOriginalPomHash() throws Exception {
        Path pom = write("pom.xml", pom("other", "1.0"));
        PlanItem item = item("one", "pom.xml", "missing", "1.0", "2.0", pom);
        PlanApplyResult failed = service().apply(request(List.of(item), false, false));
        assertEquals("FAILED", failed.plan().items().get(0).status());

        Files.writeString(pom, pom("missing", "1.0"));
        PlanApplyResult retried = service().apply(new PlanApplyRequest(failed.plan(), repository, false, true, false));

        assertEquals("APPLIED", retried.plan().items().get(0).status());
        assertTrue(Files.readString(pom).contains("<version>2.0</version>"));
    }

    private PlanApplyService service() { return service(true); }

    private PlanApplyService service(boolean clean) {
        return new PlanApplyService(new NioFileSystemAdapter(), new FakeGit(clean));
    }

    private PlanApplyRequest request(List<PlanItem> items, boolean ignore, boolean dryRun) {
        PlanDocument plan = new PlanDocument(1, "plan-1", Instant.parse("2026-01-01T00:00:00Z"),
                new PlanRepository(repository.toString(), "commit"), UpdateLevel.MINOR, items, List.of(), List.of());
        return new PlanApplyRequest(plan, repository, ignore, false, dryRun);
    }

    private PlanItem item(String id, String pomPath, String artifact, String current, String target, Path pom) {
        return new PlanItem(id, new PlanProject("g", artifact, "1", pomPath), new PlanArtifact("g", artifact),
                current, target, "DIRECT_DEPENDENCY", null, new NioFileSystemAdapter().sha256(pom), "commit",
                "PLANNED", List.of(), List.of(), Map.of());
    }

    private Path write(String relative, String content) throws Exception {
        Path path = repository.resolve(relative);
        Files.createDirectories(path.getParent() == null ? repository : path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private static String pom(String artifact, String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <dependencies>
                    <dependency>
                      <groupId>g</groupId>
                      <artifactId>%s</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(artifact, version);
    }

    private static final class FakeGit implements GitPort {
        private final boolean clean;

        private FakeGit(boolean clean) { this.clean = clean; }
        @Override public GitStatus status(Path repository) { return new GitStatus(true, clean, "commit", List.of()); }
        @Override public List<Path> trackedFiles(Path repository) { return List.of(); }
        @Override public void add(Path repository, List<Path> paths) { }
        @Override public pl.sca.fix.core.port.GitBranch createBranch(Path repository, String branchName) {
            return new pl.sca.fix.core.port.GitBranch(branchName, "commit");
        }
        @Override public pl.sca.fix.core.port.GitBranch checkoutBranch(Path repository, String branchName) {
            return new pl.sca.fix.core.port.GitBranch(branchName, "commit");
        }
        @Override public void deleteBranch(Path repository, String branchName) { }
        @Override public String commit(Path repository, String message) { return "commit"; }
        @Override public void push(Path repository, String branchName) { }
    }
}
