package pl.sca.fix.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sca")
public record ScaProperties(Workspace workspace, Jobs jobs) {
    public record Workspace(Path root) { }
    public record Jobs(String outputRetention) { }
}
