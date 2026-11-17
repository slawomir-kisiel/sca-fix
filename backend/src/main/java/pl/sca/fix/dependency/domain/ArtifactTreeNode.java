package pl.sca.fix.dependency.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "artifact_tree_nodes")
public class ArtifactTreeNode {
    @Id private UUID id;
    private UUID projectId;
    private UUID artifactVersionId;
    private UUID previousNodeId;
    @Enumerated(EnumType.STRING) private DependencyRelation relationType;
    private String scope;
    private int depth;
    private boolean direct;
    private boolean managed;

    protected ArtifactTreeNode() { }

    public ArtifactTreeNode(UUID projectId, UUID artifactVersionId, UUID previousNodeId,
                            DependencyRelation relationType, String scope, int depth,
                            boolean direct, boolean managed) {
        this.id = UUID.randomUUID(); this.projectId = projectId; this.artifactVersionId = artifactVersionId;
        this.previousNodeId = previousNodeId; this.relationType = relationType; this.scope = scope;
        this.depth = depth; this.direct = direct; this.managed = managed;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getArtifactVersionId() { return artifactVersionId; }
    public UUID getPreviousNodeId() { return previousNodeId; }
    public DependencyRelation getRelationType() { return relationType; }
    public String getScope() { return scope; }
    public int getDepth() { return depth; }
    public boolean isDirect() { return direct; }
    public boolean isManaged() { return managed; }
}
