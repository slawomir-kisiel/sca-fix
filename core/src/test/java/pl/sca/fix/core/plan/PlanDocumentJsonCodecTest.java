package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.report.UpdateLevel;

class PlanDocumentJsonCodecTest {
    @Test
    void roundTripsPortablePlan() {
        PlanDocument source = plan(new PlanItem("item-1",
                new PlanProject("com.example", "app", "1.0.0", "module/pom.xml"),
                new PlanArtifact("org.example", "library"), "1.2.3", "1.4.0",
                "DIRECT_DEPENDENCY", new PlanFinding("CVE-1", "OWASP", "path-1"),
                "hash", "commit", "PLANNED", List.of(new PlanIssue("TEST_WARNING", "warning")), List.of()));

        PlanDocument restored = new PlanDocumentJsonCodec().read(new PlanDocumentJsonCodec().write(source));

        assertEquals(source, restored);
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        String json = new String(new PlanDocumentJsonCodec().write(plan()), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"schemaVersion\" : 1", "\"schemaVersion\" : 9");
        assertThrows(PlanValidationException.class, () -> new PlanDocumentJsonCodec().read(
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsAbsolutePomPathAndConflictingChanges() {
        assertThrows(PlanValidationException.class, () -> new PlanDocumentJsonCodec().write(
                plan(item("one", "C:/checkout/pom.xml", "1.4.0"))));
        assertThrows(PlanValidationException.class, () -> new PlanDocumentJsonCodec().write(plan(
                item("one", "pom.xml", "1.4.0"), item("two", "pom.xml", "1.5.0"))));
    }

    private static PlanDocument plan(PlanItem... items) {
        return new PlanDocument(1, "plan-1", Instant.parse("2026-11-22T12:00:00Z"),
                new PlanRepository("C:/checkout", "0123456789012345678901234567890123456789"),
                UpdateLevel.MINOR, List.of(items), List.of(), List.of());
    }

    private static PlanItem item(String id, String pomPath, String target) {
        return new PlanItem(id, new PlanProject("com.example", "app", "1.0.0", pomPath),
                new PlanArtifact("org.example", "library"), "1.2.3", target,
                "DIRECT_DEPENDENCY", null, "hash", "commit", "PLANNED", List.of(), List.of());
    }
}
