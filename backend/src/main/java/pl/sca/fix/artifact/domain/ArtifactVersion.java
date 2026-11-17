package pl.sca.fix.artifact.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "artifact_versions")
public class ArtifactVersion {
    @Id private UUID id;
    private UUID artifactId;
    private String version;
    private UUID parentArtifactVersionId;
    @Enumerated(EnumType.STRING) private AnalysisStatus analysisStatus;
    private Instant analyzedAt;
    private String analyzedCommitSha;

    protected ArtifactVersion() { }
    public ArtifactVersion(UUID id, UUID artifactId, String version) {
        this.id = id; this.artifactId = artifactId; this.version = version; this.analysisStatus = AnalysisStatus.NOT_STARTED;
    }
    public UUID getId() { return id; }
    public UUID getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
    public UUID getParentArtifactVersionId() { return parentArtifactVersionId; }
    public AnalysisStatus getAnalysisStatus() { return analysisStatus; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public String getAnalyzedCommitSha() { return analyzedCommitSha; }
    public void markAnalyzed(AnalysisStatus status, Instant analyzedAt, String commitSha) { this.analysisStatus = status; this.analyzedAt = analyzedAt; this.analyzedCommitSha = commitSha; }
    public void setParentArtifactVersionId(UUID parentId) { this.parentArtifactVersionId = parentId; }
}
