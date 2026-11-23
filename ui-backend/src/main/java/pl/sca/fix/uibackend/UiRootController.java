package pl.sca.fix.uibackend;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal landing endpoint for the standalone UI backend. */
@RestController
public class UiRootController {
    @GetMapping("/ui-api/health")
    public Map<String, Object> root() {
        return Map.of("application", "sca-fix-ui-backend", "status", "UP", "apiBasePath", "/ui-api", "timestamp", Instant.now());
    }
}
