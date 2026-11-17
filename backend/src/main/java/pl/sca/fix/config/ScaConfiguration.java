package pl.sca.fix.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ScaProperties.class, MavenRepositoryProperties.class})
public class ScaConfiguration {
}
