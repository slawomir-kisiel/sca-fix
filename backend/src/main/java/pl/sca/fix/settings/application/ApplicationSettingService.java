package pl.sca.fix.settings.application;

import org.springframework.stereotype.Service;
import pl.sca.fix.settings.persistence.ApplicationSettingRepository;

@Service
public class ApplicationSettingService {
    public static final String MAVEN_ANALYSIS_POOL_SIZE = "maven.analysis.pool.size";
    private static final int DEFAULT_MAVEN_ANALYSIS_POOL_SIZE = 5;
    private final ApplicationSettingRepository settings;

    public ApplicationSettingService(ApplicationSettingRepository settings) { this.settings = settings; }

    public int mavenAnalysisPoolSize() {
        return settings.findById(MAVEN_ANALYSIS_POOL_SIZE)
                .map(item -> parsePositive(item.getSettingValue(), DEFAULT_MAVEN_ANALYSIS_POOL_SIZE))
                .orElse(DEFAULT_MAVEN_ANALYSIS_POOL_SIZE);
    }

    private int parsePositive(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
