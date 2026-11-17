package pl.sca.fix.report.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "dependency_tree_occurrences")
public class DependencyTreeOccurrence {
    @Id private UUID id;
    private UUID findingId;
    private UUID affectedArtifactId;
    private UUID projectId;
    private UUID vulnerableArtifactTreeNodeId;
    private String resolvedVersion;
    private String definitionSource;
    private boolean sourcesAvailable;

    protected DependencyTreeOccurrence() { }
    public DependencyTreeOccurrence(UUID findingId, UUID affectedArtifactId, UUID projectId,
                                    UUID vulnerableArtifactTreeNodeId, String resolvedVersion,
                                    String definitionSource, boolean sourcesAvailable) {
        this.id = UUID.randomUUID(); this.findingId = findingId; this.affectedArtifactId = affectedArtifactId;
        this.projectId = projectId; this.vulnerableArtifactTreeNodeId = vulnerableArtifactTreeNodeId; this.resolvedVersion = resolvedVersion;
        this.definitionSource = definitionSource; this.sourcesAvailable = sourcesAvailable;
    }
    public UUID getId() { return id; }
    public UUID getFindingId() { return findingId; }
    public UUID getAffectedArtifactId() { return affectedArtifactId; }
    public UUID getProjectId() { return projectId; }
    public UUID getVulnerableArtifactTreeNodeId() { return vulnerableArtifactTreeNodeId; }
    public String getResolvedVersion() { return resolvedVersion; }
    public String getDefinitionSource() { return definitionSource; }
    public boolean isSourcesAvailable() { return sourcesAvailable; }
}
