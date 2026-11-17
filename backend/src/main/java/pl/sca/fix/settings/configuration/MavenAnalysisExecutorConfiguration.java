package pl.sca.fix.settings.configuration;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pl.sca.fix.settings.application.ApplicationSettingService;

@Configuration
public class MavenAnalysisExecutorConfiguration {
    @Bean(name = "mavenAnalysisExecutor")
    public Executor mavenAnalysisExecutor(ApplicationSettingService settings) {
        int poolSize = settings.mavenAnalysisPoolSize();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("maven-analysis-");
        executor.initialize();
        return executor;
    }
}
