package pl.sca.fix.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AmbiguousStrategyTest {
    @Test
    void supportsShallowestStrategyDeclaredInCliHelp() {
        assertEquals(AmbiguousStrategy.SHALLOWEST, AmbiguousStrategy.parse("SHALLOWEST"));
        assertEquals(AmbiguousStrategy.SHALLOWEST, AmbiguousStrategy.parse("SHALLOWLESS"));
    }
}
