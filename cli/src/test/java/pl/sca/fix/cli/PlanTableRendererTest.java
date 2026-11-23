package pl.sca.fix.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.pipeline.ScaFixPipeline;
import pl.sca.fix.core.workspace.WorkspaceState;

class PlanTableRendererTest {
    private final PlanTableRenderer renderer = new PlanTableRenderer();

    @Test
    void shortensGroupBeforeArtifactAndKeepsArtifact() {
        String dependency = "org.apache.collections.deep.group:" + "a".repeat(40);
        String shortened = renderer.shortenDependency(dependency);

        assertTrue(shortened.length() <= 60);
        assertTrue(shortened.endsWith(":" + "a".repeat(40)));
        assertTrue(shortened.startsWith("o."));
    }

    @Test
    void choosesTheLeastAbbreviatedGroupThatFits() {
        String shortened = renderer.shortenDependency("org.apache.collections:artifact");

        assertEquals("org.apache.collections:artifact", shortened);
    }

    @Test
    void rendersContinuousBordersAndWidthsBasedOnContent() {
        String table = renderer.render(List.of(new PlanTableRenderer.PlanRow(
                "org.example:library", "1.2.3", "1.4.0", "service-a", "PROPERTY", "PLANNED")));
        String[] lines = table.split("\\R");
        assertEquals(5, lines.length);
        assertEquals(lines[0].length(), lines[1].length());
        assertEquals(lines[0].length(), lines[2].length());
        assertEquals(lines[0].length(), lines[4].length());
        assertTrue(lines[0].startsWith("+") && lines[0].endsWith("+"));
        assertTrue(lines[1].startsWith("|") && lines[1].endsWith("|"));
    }

    @Test
    void rendersWarningsOnlyWhenPresentAndTruncatesLongMessages() {
        WorkspaceState.Issue warning = new WorkspaceState.Issue(
                "NO_VERSION_RECOMMENDATION",
                "x".repeat(600),
                "PLANNING",
                Instant.parse("2026-11-23T10:00:00Z"));

        String table = renderer.renderWarnings(List.of(warning));

        assertTrue(table.startsWith("Warnings:\n"));
        assertTrue(table.contains("| STAGE"));
        assertTrue(table.contains("PLANNING"));
        assertTrue(table.contains("NO_VERSION_RECOMMENDATION"));
        assertTrue(table.contains("x".repeat(509) + "..."));
        assertEquals("", renderer.renderWarnings(List.of()));
    }

    @Test
    void rendersStageProgressHeaderWithDescription() {
        String table = PlanTableRenderer.renderStageHeader(
                new ScaFixPipeline.StageProgress(2, 9, WorkspaceState.Stage.PLANNING));
        String[] lines = table.split("\\R");

        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("Stage: 2 of 9"));
        assertTrue(lines[1].contains("PLANNING"));
        assertTrue(lines[1].contains("Builds PlanItems with recommended dependency updates"));
        assertEquals(lines[0].length(), lines[1].length());
        assertEquals(lines[0].length(), lines[2].length());
    }
}
