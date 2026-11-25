package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.maven.EffectivePom;
import pl.sca.fix.core.maven.VersionCacheDocument;
import pl.sca.fix.core.model.ArtifactCoordinate;
import pl.sca.fix.core.report.CanonicalScanReport;
import pl.sca.fix.core.report.CorrelationEntry;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.CorrelationStatus;
import pl.sca.fix.core.report.DependencyTreeOccurrence;
import pl.sca.fix.core.report.ReportStatus;
import pl.sca.fix.core.report.Severity;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.report.VulnerableLibrary;

class AutomaticPlanningServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsOneItemForMatchedLibrary() {
        VulnerableLibrary library = library();
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(
                report(library), correlation(library, CorrelationStatus.MATCHED,
                        List.of(new DependencyTreeOccurrence("project", "project/0", "0", 1, null, "1.2.3", "DEPENDENCY"))),
                List.of(project()), Map.of("g:a", List.of(version("1.4.0"))), Path.of("C:/checkout"),
                "0123456789012345678901234567890123456789", UpdateLevel.MINOR,
                AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals(1, result.plan().items().size());
        assertEquals("1.4.0", result.plan().items().get(0).targetVersion());
    }

    @Test
    void skipsAmbiguousOccurrenceByDefaultWhenSourcesDiffer() {
        VulnerableLibrary library = library();
        List<DependencyTreeOccurrence> occurrences = List.of(
                new DependencyTreeOccurrence("one", "one/0", "0", 1, null, "1.2.3", "DEPENDENCY"),
                new DependencyTreeOccurrence("two", "two/0", "0", 1, null, "1.2.3", "DEPENDENCY"));
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(report(library),
                correlation(library, CorrelationStatus.AMBIGUOUS, occurrences),
                List.of(project("one"), project("two")), Map.of("g:a", List.of(version("1.4.0"))),
                Path.of("C:/checkout"), "0123456789012345678901234567890123456789", UpdateLevel.MINOR,
                AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals(0, result.plan().items().size());
        assertEquals(1, result.summary().skippedItems());
    }

    @Test
    void deduplicatesSameChangeFoundThroughSeveralDependencyPaths() {
        VulnerableLibrary library = library();
        List<DependencyTreeOccurrence> occurrences = List.of(
                new DependencyTreeOccurrence("project", "project/0", "0", 1, null, "1.2.3", "DEPENDENCY"),
                new DependencyTreeOccurrence("project", "project/1", "1", 1, null, "1.2.3", "DEPENDENCY"));

        AutomaticPlanningResult result = new AutomaticPlanningService().plan(report(library),
                correlation(library, CorrelationStatus.AMBIGUOUS, occurrences), List.of(project()),
                Map.of("g:a", List.of(version("1.4.0"))), Path.of("C:/checkout"),
                "0123456789012345678901234567890123456789", UpdateLevel.MINOR,
                AmbiguousStrategy.ALL, false);

        assertEquals(1, result.plan().items().size());
        assertEquals(1, result.summary().plannedItems());
    }

    @Test
    void createsCveOverrideForTransitiveVulnerability() {
        VulnerableLibrary library = library();
        PlanningProject project = new PlanningProject("project", temporaryDirectory.resolve("pom.xml"),
                new EffectivePom(new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                        "p", "project", "1.0.0", null, List.of(), List.of(), List.of()), true);
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(
                report(library), correlation(library, CorrelationStatus.MATCHED,
                        List.of(new DependencyTreeOccurrence("project", "project/0", "0", 1, null,
                                "1.2.3", "DEPENDENCY"))), List.of(project),
                Map.of("g:a", List.of(version("1.4.0"))), temporaryDirectory,
                "0123456789012345678901234567890123456789", UpdateLevel.MINOR,
                AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals("CVE_OVERRIDE", result.plan().items().get(0).strategy());
    }

    @Test
    void usesDedicatedUpdateLevelForCveOverride() {
        VulnerableLibrary library = library();
        PlanningProject project = new PlanningProject("project", temporaryDirectory.resolve("pom.xml"),
                new EffectivePom(new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                        "p", "project", "1.0.0", null, List.of(), List.of(), List.of()), true);
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(
                report(library), correlation(library, CorrelationStatus.MATCHED,
                        List.of(new DependencyTreeOccurrence("project", "project/0", "0", 1, null,
                                "1.2.3", "DEPENDENCY"))), List.of(project),
                Map.of("g:a", List.of(version("1.2.4"), version("1.3.0"))), temporaryDirectory,
                "0123456789012345678901234567890123456789", UpdateLevel.MAJOR, UpdateLevel.PATCH,
                AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals("CVE_OVERRIDE", result.plan().items().get(0).strategy());
        assertEquals("1.2.4", result.plan().items().get(0).targetVersion());
    }

    @Test
    void detectsPropertyDeclarationInChangeProject() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        Files.writeString(pom, """
                <project><dependencies><dependency>
                  <groupId>g</groupId><artifactId>a</artifactId>
                  <version>${a.version}</version>
                </dependency></dependencies></project>
                """);
        EffectivePom effectivePom = new EffectivePom(
                new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                "p", "project", "1.0.0", null,
                List.of(new EffectivePom.Dependency("g", "a", "1.2.3", null, null, null,
                        new EffectivePom.VersionLocation("project", 3))), List.of(), List.of());
        PlanningProject project = new PlanningProject("project", pom, effectivePom, true);
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(
                report(library()), correlation(library(), CorrelationStatus.MATCHED,
                        List.of(new DependencyTreeOccurrence("project", "project/0", "0", 1, null,
                                "1.2.3", "DEPENDENCY"))), List.of(project),
                Map.of("g:a", List.of(version("1.4.0"))), temporaryDirectory,
                "0123456789012345678901234567890123456789", UpdateLevel.MINOR,
                AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals("PROPERTY", result.plan().items().get(0).strategy());
        assertEquals("a.version", result.plan().items().get(0).details().get("propertyName"));
    }

    @Test
    void prefersDirectEffectiveDependencyVersionOverDependencyManagementVersion() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        Files.writeString(pom, """
                <project><dependencies><dependency>
                  <groupId>g</groupId><artifactId>a</artifactId><version>${a.version}</version>
                </dependency></dependencies></project>
                """);
        EffectivePom effectivePom = new EffectivePom(
                new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                "p", "project", "1.0.0", null,
                List.of(new EffectivePom.Dependency("g", "a", "1.6.7", null, null, null,
                        new EffectivePom.VersionLocation("project", 2))),
                List.of(new EffectivePom.Dependency("g", "a", "1.2.13", null, null, null, null)), List.of());
        PlanningProject project = new PlanningProject("project", pom, effectivePom, true);

        AutomaticPlanningResult result = new AutomaticPlanningService().plan(
                report(library()), correlation(library(), CorrelationStatus.MATCHED,
                        List.of(new DependencyTreeOccurrence("project", "project/0", "0", 1, null,
                                "1.2.13", "DEPENDENCY"))), List.of(project),
                Map.of("g:a", List.of(version("1.6.8"))), temporaryDirectory,
                "0123456789012345678901234567890123456789", UpdateLevel.MINOR,
                AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals("PROPERTY", result.plan().items().get(0).strategy());
        assertEquals("1.6.7", result.plan().items().get(0).currentVersion());
        assertEquals("1.6.8", result.plan().items().get(0).targetVersion());
    }

    @Test
    void doesNotCreateDirectDependencyForVersionOwnedByUnregisteredParent() throws Exception {
        Path pom = temporaryDirectory.resolve("child-pom.xml");
        Files.writeString(pom, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <dependencies><dependency>
                    <groupId>g</groupId><artifactId>a</artifactId>
                  </dependency></dependencies>
                </project>
                """);
        EffectivePom effectivePom = new EffectivePom(
                new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                "p", "project", "1.0.0", null,
                List.of(new EffectivePom.Dependency("g", "a", "1.2.3", null, null, null,
                        new EffectivePom.VersionLocation("parent", 12))), List.of(), List.of());
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(
                report(library()), correlation(library(), CorrelationStatus.MATCHED,
                        List.of(new DependencyTreeOccurrence("project", "project/0", "0", 1, null,
                                "1.2.3", "DEPENDENCY"))),
                List.of(new PlanningProject("project", pom, effectivePom, true)),
                Map.of("g:a", List.of(version("1.4.0"))), temporaryDirectory,
                "0123456789012345678901234567890123456789", UpdateLevel.MINOR,
                AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals("CVE_OVERRIDE", result.plan().items().get(0).strategy());
        assertEquals("project", result.plan().items().get(0).project().artifactId());
    }

    @Test
    void doesNotTreatFollowingDependencyPropertyAsVersionOfUnversionedDependency() throws Exception {
        Path pom = temporaryDirectory.resolve("pom.xml");
        Files.writeString(pom, """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>g</groupId>
                      <artifactId>other</artifactId>
                      <version>${other.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        EffectivePom effectivePom = new EffectivePom(
                new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                "p", "project", "1.0.0", null,
                List.of(new EffectivePom.Dependency("com.fasterxml.jackson.core", "jackson-databind", "2.20.1",
                        "provided", null, null, new EffectivePom.VersionLocation("project", 4))),
                List.of(), List.of());
        PlanningProject project = new PlanningProject("project", pom, effectivePom, true);
        VulnerableLibrary library = new VulnerableLibrary("com.fasterxml.jackson.core", "jackson-databind", "2.20.1",
                List.of("CVE-1"), Severity.HIGH, new java.math.BigDecimal("7.5"), List.of());

        AutomaticPlanningResult result = new AutomaticPlanningService().plan(
                report(library), correlation(library, CorrelationStatus.MATCHED,
                        List.of(new DependencyTreeOccurrence("project", "project/0", "0", 1, null,
                                "2.20.1", "DEPENDENCY"))), List.of(project),
                Map.of("com.fasterxml.jackson.core:jackson-databind", List.of(version("2.21.0"))),
                temporaryDirectory, "0123456789012345678901234567890123456789", UpdateLevel.MINOR,
                AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals("CVE_OVERRIDE", result.plan().items().get(0).strategy());
    }

    private static VulnerableLibrary library() {
        return new VulnerableLibrary("g", "a", "1.2.3", List.of("CVE-1"), Severity.HIGH,
                new java.math.BigDecimal("7.5"), List.of());
    }
    private static CanonicalScanReport report(VulnerableLibrary library) {
        return new CanonicalScanReport(1, ReportStatus.PARSED, null, List.of(library), List.of(), List.of(), List.of());
    }
    private static CorrelationResult correlation(VulnerableLibrary library, CorrelationStatus status,
                                                  List<DependencyTreeOccurrence> occurrences) {
        return new CorrelationResult(1, List.of(new CorrelationEntry(
                library.groupId() + ":" + library.artifactId() + ":" + library.version(), status, occurrences, List.of())), List.of(), List.of());
    }
    private static PlanningProject project() { return project("project"); }
    private static PlanningProject project(String id) {
        EffectivePom pom = new EffectivePom(new EffectivePom.Metadata("hash", Instant.parse("2026-01-01T00:00:00Z")),
                "p", id, "1.0.0", null,
                List.of(new EffectivePom.Dependency("g", "a", "1.2.3", null, null, null, null)), List.of(), List.of());
        return new PlanningProject(id, Path.of("C:/checkout/" + id + "/pom.xml"), pom, true);
    }
    private static VersionCacheDocument.VersionEntry version(String value) {
        return new VersionCacheDocument.VersionEntry(value, List.of("test"), "RELEASE");
    }
}
