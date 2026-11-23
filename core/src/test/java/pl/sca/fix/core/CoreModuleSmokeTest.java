package pl.sca.fix.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class CoreModuleSmokeTest {
    @Test
    void exposesSlf4jApiWithoutSpringRuntime() {
        assertNotNull(LoggerFactory.getLogger(CoreModuleSmokeTest.class));
    }
}
