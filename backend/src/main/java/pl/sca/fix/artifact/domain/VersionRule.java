package pl.sca.fix.artifact.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "version_rules")
public class VersionRule {
    @Id private UUID id;
    private UUID artifactId;
    private String pattern;
    private boolean allowSnapshots;
    private Instant updatedAt;

    protected VersionRule() { }

    public VersionRule(UUID artifactId, String pattern, boolean allowSnapshots) {
        this.id = UUID.randomUUID();
        this.artifactId = artifactId;
        this.pattern = pattern;
        this.allowSnapshots = allowSnapshots;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getArtifactId() { return artifactId; }
    public String getPattern() { return pattern; }
    public boolean isAllowSnapshots() { return allowSnapshots; }
    public Instant getUpdatedAt() { return updatedAt; }
}
