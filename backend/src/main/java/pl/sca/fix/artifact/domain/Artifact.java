package pl.sca.fix.artifact.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "artifacts")
public class Artifact {
    @Id private UUID id;
    private String groupId;
    private String artifactId;
    private String displayName;
    private UUID projectId;

    protected Artifact() { }
    public Artifact(UUID id, String groupId, String artifactId, UUID projectId) {
        this.id = id; this.groupId = groupId; this.artifactId = artifactId; this.projectId = projectId;
        this.displayName = groupId + ":" + artifactId;
    }
    public UUID getId() { return id; }
    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getDisplayName() { return displayName; }
    public UUID getProjectId() { return projectId; }
    public void assignProject(UUID projectId) { if (this.projectId == null) this.projectId = projectId; }
}
