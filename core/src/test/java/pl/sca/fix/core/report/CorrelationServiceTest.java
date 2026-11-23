package pl.sca.fix.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.maven.DependencyTree;
import pl.sca.fix.core.maven.DependencyTreeNode;
import pl.sca.fix.core.model.ArtifactCoordinate;

class CorrelationServiceTest {
    @Test
    void minorMatchingUsesMajorLineInsteadOfExactVersion() {
        CanonicalScanReport report = report("1.2.3");
        DependencyTree tree = tree("1.4.2", "0.1");

        CorrelationEntry entry = new CorrelationService().correlate(report,
                List.of(new ProjectTree("project", null, tree)), UpdateLevel.MINOR).entries().get(0);

        assertEquals(CorrelationStatus.MATCHED, entry.status());
        assertEquals("0.1", entry.occurrences().get(0).vulnerableNodeId());
    }

    @Test
    void minorMatchingRejectsHigherOrLowerMajor() {
        CanonicalScanReport report = report("1.2.3");
        CorrelationService service = new CorrelationService();

        assertEquals(CorrelationStatus.ORPHAN, service.correlate(report,
                List.of(new ProjectTree("p", null, tree("2.0.1", "0"))), UpdateLevel.MINOR)
                .entries().get(0).status());
        assertEquals(CorrelationStatus.ORPHAN, service.correlate(report,
                List.of(new ProjectTree("p", null, tree("0.0.1", "0"))), UpdateLevel.MINOR)
                .entries().get(0).status());
    }

    @Test
    void patchMatchingKeepsMajorAndMinorLine() {
        CanonicalScanReport report = report("1.2.3");
        CorrelationService service = new CorrelationService();
        assertEquals(CorrelationStatus.MATCHED, service.correlate(report,
                List.of(new ProjectTree("p", null, tree("1.2.9", "0"))), UpdateLevel.PATCH)
                .entries().get(0).status());
        assertEquals(CorrelationStatus.ORPHAN, service.correlate(report,
                List.of(new ProjectTree("p", null, tree("1.3.0", "0"))), UpdateLevel.PATCH)
                .entries().get(0).status());
    }

    @Test
    void createsSeparateOccurrencesForTwoBranches() {
        DependencyTreeNode first = node("0", "1.4.2", node("0.0", "1.0.0"));
        DependencyTreeNode second = node("1", "1.4.2", node("1.0", "1.0.0"));
        DependencyTree tree = new DependencyTree(root(first, second),
                Map.of("g:a", List.of("0", "1")));

        CorrelationEntry entry = new CorrelationService().correlate(report("1.2.3"),
                List.of(new ProjectTree("project", null, tree)), UpdateLevel.MINOR).entries().get(0);

        assertEquals(CorrelationStatus.AMBIGUOUS, entry.status());
        assertEquals(List.of("project/0", "project/1"), entry.occurrences().stream()
                .map(DependencyTreeOccurrence::pathId).toList());
        assertTrue(entry.occurrences().stream().allMatch(value -> value.previousNodeId() == null));
    }

    private static CanonicalScanReport report(String version) {
        return new CanonicalScanReport(1, ReportStatus.PARSED, null,
                List.of(new VulnerableLibrary("g", "a", version, List.of("CVE-1"), Severity.HIGH,
                        new java.math.BigDecimal("7.5"), List.of())), List.of(), List.of(), List.of());
    }

    private static DependencyTree tree(String version, String nodeId) {
        return new DependencyTree(root(node(nodeId, version)), Map.of("g:a", List.of(nodeId)));
    }

    private static DependencyTreeNode root(DependencyTreeNode... children) {
        return new DependencyTreeNode(null, new ArtifactCoordinate("root", "project", "1"), "", false, List.of(children));
    }

    private static DependencyTreeNode node(String id, String version, DependencyTreeNode... children) {
        return new DependencyTreeNode(id, new ArtifactCoordinate("g", "a", version), "compile", false, List.of(children));
    }
}
