package pl.sca.fix.report.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_scan_reports")
public class SecurityScanReport {
    @Id private UUID id;
    private UUID repositoryId;
    private UUID contextProjectId;
    @Column(name = "context_pom_path", length = 2000) private String contextPomPath;
    private String contextBranch;
    private String contextCommitSha;
    private String contextSource;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private String source;
    private String format;
    private String formatVersion;
    private Instant importedAt;
    private Instant scanDate;
    @Enumerated(EnumType.STRING) private ReportStatus status;

    protected SecurityScanReport() { }

    public SecurityScanReport(UUID id, UUID repositoryId, String originalFilename, String contentType,
                              long sizeBytes, String sha256, Instant importedAt) {
        this(id, repositoryId, originalFilename, contentType, sizeBytes, sha256, importedAt, null, null, null);
    }

    public SecurityScanReport(UUID id, UUID repositoryId, String originalFilename, String contentType,
                              long sizeBytes, String sha256, Instant importedAt,
                              String source, String format, String formatVersion) {
        this.id = id; this.repositoryId = repositoryId; this.originalFilename = originalFilename;
        this.contentType = contentType; this.sizeBytes = sizeBytes; this.sha256 = sha256;
        this.source = source; this.format = format; this.formatVersion = formatVersion;
        this.importedAt = importedAt; this.status = ReportStatus.NOT_STARTED;
    }

    public UUID getId() { return id; }
    public UUID getRepositoryId() { return repositoryId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getSource() { return source; }
    public String getFormat() { return format; }
    public String getFormatVersion() { return formatVersion; }
    public Instant getImportedAt() { return importedAt; }
    public Instant getScanDate() { return scanDate; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public void applyContext(UUID repositoryId, UUID projectId, String pomPath, String branch, String commitSha) {
        this.repositoryId = repositoryId; this.contextProjectId = projectId; this.contextPomPath = pomPath;
        this.contextBranch = branch; this.contextCommitSha = commitSha; this.contextSource = "USER_INPUT";
    }
    public UUID getContextProjectId() { return contextProjectId; }
    public String getContextPomPath() { return contextPomPath; }
    public String getContextBranch() { return contextBranch; }
    public String getContextCommitSha() { return contextCommitSha; }
    public String getContextSource() { return contextSource; }
}
