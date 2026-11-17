package pl.sca.fix.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    private UUID id;
    private UUID repositoryId;
    private String name;
    @Column(name = "pom_path", length = 2000)
    private String pomPath;
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String parentGroupId;
    private String parentArtifactId;
    private String parentVersion;
    private String baseBranch;
    private String baseCommitSha;
    @Enumerated(EnumType.STRING)
    private ProjectStatus status;
    private UUID mavenAnalysisJobId;
    @Enumerated(EnumType.STRING)
    private MavenAnalysisStatus mavenAnalysisStatus;
    private Instant mavenAnalysisStartedAt;
    private Instant mavenAnalysisFinishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected Project() { }

    public Project(UUID id, UUID repositoryId, String name, String pomPath, String groupId,
                   String artifactId, String version, String packaging, String parentGroupId,
                   String parentArtifactId, String parentVersion, String baseBranch, String baseCommitSha) {
        this.id = id;
        this.repositoryId = repositoryId;
        this.name = name;
        this.pomPath = pomPath;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.parentGroupId = parentGroupId;
        this.parentArtifactId = parentArtifactId;
        this.parentVersion = parentVersion;
        this.baseBranch = baseBranch;
        this.baseCommitSha = baseCommitSha;
        this.status = ProjectStatus.REGISTERED;
        this.mavenAnalysisStatus = MavenAnalysisStatus.REGISTERED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRepositoryId() { return repositoryId; }
    public String getName() { return name; }
    public String getPomPath() { return pomPath; }
    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
    public String getPackaging() { return packaging; }
    public String getParentGroupId() { return parentGroupId; }
    public String getParentArtifactId() { return parentArtifactId; }
    public String getParentVersion() { return parentVersion; }
    public String getBaseBranch() { return baseBranch; }
    public String getBaseCommitSha() { return baseCommitSha; }
    public ProjectStatus getStatus() { return status; }
    public UUID getMavenAnalysisJobId() { return mavenAnalysisJobId; }
    public MavenAnalysisStatus getMavenAnalysisStatus() { return mavenAnalysisStatus; }
    public Instant getMavenAnalysisStartedAt() { return mavenAnalysisStartedAt; }
    public Instant getMavenAnalysisFinishedAt() { return mavenAnalysisFinishedAt; }
    public boolean hasActiveMavenAnalysis() {
        return mavenAnalysisStatus == MavenAnalysisStatus.QUEUED || mavenAnalysisStatus == MavenAnalysisStatus.ANALYZING;
    }
    public void queueMavenAnalysis(UUID jobId) {
        this.mavenAnalysisJobId = jobId;
        this.mavenAnalysisStatus = MavenAnalysisStatus.QUEUED;
        this.mavenAnalysisStartedAt = null;
        this.mavenAnalysisFinishedAt = null;
        this.updatedAt = Instant.now();
    }
    public void startMavenAnalysis() {
        this.mavenAnalysisStatus = MavenAnalysisStatus.ANALYZING;
        this.mavenAnalysisStartedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    public void finishMavenAnalysis(boolean success) {
        this.mavenAnalysisStatus = success ? MavenAnalysisStatus.ANALYZED : MavenAnalysisStatus.FAILED;
        this.mavenAnalysisFinishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
