package pl.sca.fix.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sca.maven")
public class MavenRepositoryProperties {
    private Duration cacheTtl = Duration.ofHours(1);
    private List<Repository> repositories = new ArrayList<>();

    public Duration getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
    public List<Repository> getRepositories() { return repositories; }
    public void setRepositories(List<Repository> repositories) { this.repositories = repositories; }

    public static class Repository {
        private String id;
        private String url;
        private String username;
        private String password;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
