package pl.sca.fix.integration.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MavenVersionComparatorTest {
    @Test
    void comparesNumericSegmentsInsteadOfLexicographicText() {
        assertTrue(MavenVersionComparator.compareVersions("4.10", "4.6.0") > 0);
        assertTrue(MavenVersionComparator.compareVersions("4.6.0", "4.10") < 0);
    }

    @Test
    void keepsMajorLineForRegularVersions() {
        assertTrue(MavenVersionComparator.sameDevelopmentLine("4.0", "4.6.0"));
        assertTrue(!MavenVersionComparator.sameDevelopmentLine("4.0", "5.0"));
    }

    @Test
    void keepsMinorLineForZeroMajorVersions() {
        assertTrue(MavenVersionComparator.sameDevelopmentLine("0.3.1", "0.3.9"));
        assertTrue(!MavenVersionComparator.sameDevelopmentLine("0.3.1", "0.4.0"));
    }

    @Test
    void recognizesSnapshots() {
        assertEquals(true, MavenVersionComparator.isSnapshot("4.6.0-SNAPSHOT"));
    }
}
