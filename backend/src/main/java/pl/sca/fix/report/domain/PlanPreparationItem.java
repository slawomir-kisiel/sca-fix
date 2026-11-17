package pl.sca.fix.report.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_preparation_items")
public class PlanPreparationItem {
    @Id private UUID id;
    private UUID runId;
    private String groupId;
    private String artifactId;
    private String version;
    private Double maxCvssScore;
    private UUID jobId;
    private UUID findingId;
    private UUID occurrenceId;
    @Enumerated(EnumType.STRING) private PlanPreparationStatus status;
    private String message;
    private Instant createdAt;
    private Instant updatedAt;

    protected PlanPreparationItem() { }
    public PlanPreparationItem(UUID runId, String groupId, String artifactId, String version,
                               Double maxCvssScore, UUID jobId) {
        this.id = UUID.randomUUID(); this.runId = runId; this.groupId = groupId; this.artifactId = artifactId;
        this.version = version; this.maxCvssScore = maxCvssScore; this.jobId = jobId;
        this.status = PlanPreparationStatus.QUEUED; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
    public Double getMaxCvssScore() { return maxCvssScore; }
    public UUID getJobId() { return jobId; }
    public UUID getFindingId() { return findingId; }
    public UUID getOccurrenceId() { return occurrenceId; }
    public PlanPreparationStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void start() { status = PlanPreparationStatus.PLANNING; updatedAt = Instant.now(); }
    public void planned(UUID findingId, UUID occurrenceId) { this.findingId = findingId; this.occurrenceId = occurrenceId; status = PlanPreparationStatus.PLANNED; updatedAt = Instant.now(); }
    public void skipped(String message) { status = PlanPreparationStatus.SKIPPED; this.message = message; updatedAt = Instant.now(); }
    public void failed(String message) { status = PlanPreparationStatus.FAILED; this.message = message; updatedAt = Instant.now(); }
}
