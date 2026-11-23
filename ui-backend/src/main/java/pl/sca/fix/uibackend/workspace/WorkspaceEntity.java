package pl.sca.fix.uibackend.workspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "ui_workspaces")
public class WorkspaceEntity {
    @Id private UUID id;
    @Column(nullable = false) private String repositoryPath;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected WorkspaceEntity() {}
    public WorkspaceEntity(UUID id, String repositoryPath, String status, Instant createdAt, Instant updatedAt) {
        this.id = id; this.repositoryPath = repositoryPath; this.status = status;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }
    public UUID getId() { return id; }
    public String getRepositoryPath() { return repositoryPath; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

