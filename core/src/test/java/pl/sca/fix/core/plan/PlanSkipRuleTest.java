package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlanSkipRuleTest {
    @Test
    void matchesGroupArtifactAndVersionPrefix() {
        PlanItem item = new PlanItem("item", new PlanProject("g", "app", "1", "pom.xml"),
                new PlanArtifact("org.example", "library"), "3.12.4", "3.13.0", "PROPERTY",
                null, "hash", "commit", "PLANNED", List.of(), List.of());

        assertTrue(PlanSkipRule.parse("org.example").matches(item));
        assertTrue(PlanSkipRule.parse("org.example:library").matches(item));
        assertTrue(PlanSkipRule.parse("org.example:library:3.").matches(item));
        assertFalse(PlanSkipRule.parse("org.example:library:4.").matches(item));
        assertFalse(PlanSkipRule.parse("org.other").matches(item));
    }

    @Test
    void rejectsMalformedDescriptor() {
        assertThrows(IllegalArgumentException.class, () -> PlanSkipRule.parse("org.example:library:3.12:extra"));
        assertThrows(IllegalArgumentException.class, () -> PlanSkipRule.parse(":library"));
    }
}
