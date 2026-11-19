package pl.sca.fix.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sca.maven")
public class MavenRepositoryProperties {
    private Duration cacheTtl = Duration.ofHours(1);
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(15);
    private Duration unavailableBlockDuration = Duration.ofMinutes(10);
    private boolean useM2SettingsProxy = true;
    private String settingsXmlPath;
    private List<Repository> repositories = new ArrayList<>();

    public Duration getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getUnavailableBlockDuration() { return unavailableBlockDuration; }
    public void setUnavailableBlockDuration(Duration unavailableBlockDuration) { this.unavailableBlockDuration = unavailableBlockDuration; }
    public boolean isUseM2SettingsProxy() { return useM2SettingsProxy; }
    public void setUseM2SettingsProxy(boolean useM2SettingsProxy) { this.useM2SettingsProxy = useM2SettingsProxy; }
    public String getSettingsXmlPath() { return settingsXmlPath; }
    public void setSettingsXmlPath(String settingsXmlPath) { this.settingsXmlPath = settingsXmlPath; }
    public List<Repository> getRepositories() { return repositories; }
    public void setRepositories(List<Repository> repositories) { this.repositories = repositories; }

    public static class Repository {
        private String id;
        private String url;
        private String username;
        private String password;
        private boolean releases = true;
        private boolean snapshots;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isReleases() { return releases; }
        public void setReleases(boolean releases) { this.releases = releases; }
        public boolean isSnapshots() { return snapshots; }
        public void setSnapshots(boolean snapshots) { this.snapshots = snapshots; }
    }
}
