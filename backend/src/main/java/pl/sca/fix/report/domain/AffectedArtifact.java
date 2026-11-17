package pl.sca.fix.report.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "affected_artifacts")
public class AffectedArtifact {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) private VulnerabilityFinding finding;
    private String purl;
    private String groupId;
    private String artifactId;
    private String reportedVersion;
    private String fileName;
    private String filePath;
    private String sha1;
    private String sha256;

    protected AffectedArtifact() { }
    public AffectedArtifact(VulnerabilityFinding finding, pl.sca.fix.report.canonical.AffectedArtifact source) {
        this.finding = finding; this.purl = source.purl(); this.groupId = source.groupId(); this.artifactId = source.artifactId();
        this.reportedVersion = source.version(); this.fileName = source.fileName(); this.filePath = source.filePath();
        this.sha1 = source.sha1(); this.sha256 = source.sha256();
    }
    public UUID getId() { return id; }
    public String getPurl() { return purl; }
    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getReportedVersion() { return reportedVersion; }
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public String getSha1() { return sha1; }
    public String getSha256() { return sha256; }
}
