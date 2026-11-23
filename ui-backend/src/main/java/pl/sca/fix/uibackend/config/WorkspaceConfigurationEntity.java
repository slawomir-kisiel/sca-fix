package pl.sca.fix.uibackend.config;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ui_workspace_configurations")
public class WorkspaceConfigurationEntity {
    @Id private UUID workspaceId;
    @Lob @Column(nullable = false) private String content;
    @Column(nullable = false) private Instant updatedAt;

    protected WorkspaceConfigurationEntity() {}
    WorkspaceConfigurationEntity(UUID workspaceId, String content, Instant updatedAt) {
        this.workspaceId = workspaceId; this.content = content; this.updatedAt = updatedAt;
    }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getContent() { return content; }
    public Instant getUpdatedAt() { return updatedAt; }
}

