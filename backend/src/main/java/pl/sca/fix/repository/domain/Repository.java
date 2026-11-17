package pl.sca.fix.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repositories")
public class Repository {
    @Id
    private UUID id;
    private String provider;
    @Column(name = "repository_url", length = 2000)
    private String url;
    @Column(name = "clone_path", nullable = false, length = 2000)
    private String clonePath;
    private String credentialsRef;
    private String defaultBranch;
    private String baseCommitSha;
    @Enumerated(EnumType.STRING)
    private RepositoryStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    protected Repository() { }

    public Repository(UUID id, String provider, String url, String clonePath) {
        this.id = id;
        this.provider = provider;
        this.url = url;
        this.clonePath = clonePath;
        this.status = RepositoryStatus.READY;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getUrl() { return url; }
    public String getClonePath() { return clonePath; }
    public String getCredentialsRef() { return credentialsRef; }
    public String getDefaultBranch() { return defaultBranch; }
    public String getBaseCommitSha() { return baseCommitSha; }
    public RepositoryStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateCheckoutMetadata(String branch, String commitSha) {
        this.defaultBranch = branch;
        this.baseCommitSha = commitSha;
        this.updatedAt = Instant.now();
    }
}
