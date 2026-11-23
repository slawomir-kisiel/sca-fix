package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.sca.fix.core.maven.VersionCacheDocument;
import pl.sca.fix.core.report.CanonicalScanReport;
import pl.sca.fix.core.report.CorrelationEntry;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.CorrelationStatus;
import pl.sca.fix.core.report.DependencyTreeOccurrence;
import pl.sca.fix.core.report.ReportStatus;
import pl.sca.fix.core.report.Severity;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.report.VulnerableLibrary;

class AutomaticPlanningWarningTest {
    @Test
    void warningsAreDetailedAndUnique() {
        VulnerableLibrary library = new VulnerableLibrary("g", "a", "1.2.3", List.of("CVE-1"),
                Severity.HIGH, new java.math.BigDecimal("7.5"), List.of());
        CorrelationEntry entry = new CorrelationEntry("g:a:1.2.3", CorrelationStatus.AMBIGUOUS,
                List.of(new DependencyTreeOccurrence("missing-one", "missing-one/0", "0", 1, null, "1.2.3", "DEPENDENCY"),
                        new DependencyTreeOccurrence("missing-two", "missing-two/1", "1", 1, null, "1.2.3", "DEPENDENCY")), List.of());
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(
                new CanonicalScanReport(1, ReportStatus.PARSED, null, List.of(library), List.of(), List.of(), List.of()),
                new CorrelationResult(1, List.of(entry), List.of(), List.of()), List.of(),
                Map.<String, List<VersionCacheDocument.VersionEntry>>of(), Path.of("C:/checkout"), "commit",
                UpdateLevel.MINOR, AmbiguousStrategy.COMMON_SOURCE, false);

        assertEquals(1, result.plan().warnings().size());
        assertEquals("AMBIGUOUS_OCCURRENCE", result.plan().warnings().get(0).reason());
        assertTrue(result.plan().warnings().get(0).message().contains("library=g:a:1.2.3"));
        assertTrue(result.plan().warnings().get(0).message().contains("occurrences=2"));
    }
}
