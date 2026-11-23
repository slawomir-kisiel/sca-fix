package pl.sca.fix.uibackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.sca.fix.core.git.GitProcessAdapter;
import pl.sca.fix.core.maven.SystemProcessRunner;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitClonePort;
import pl.sca.fix.core.discovery.ProjectDiscoveryService;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.maven.MavenProcessAdapter;
import pl.sca.fix.core.maven.MavenToolConfiguration;
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.core.maven.SystemProcessRunner;

@Configuration
public class CoreAdapterConfiguration {
    @Bean
    GitProcessAdapter gitAdapter() { return new GitProcessAdapter(new SystemProcessRunner()); }
    @Bean
    ProjectDiscoveryService projectDiscoveryService(GitPort gitPort) {
        return new ProjectDiscoveryService(gitPort, new NioFileSystemAdapter());
    }
    @Bean
    MavenPort mavenPort() {
        return new MavenProcessAdapter(new SystemProcessRunner(), new NioFileSystemAdapter(),
                MavenToolConfiguration.defaults(System.getProperty("os.name", "").toLowerCase().contains("win")));
    }
}

