package pl.sca.fix.report.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_items")
public class PlanItem {
    @Id private UUID id;
    private UUID updatePlanId;
    private UUID findingId;
    private UUID occurrenceId;
    private UUID targetProjectId;
    private UUID changePointTreeNodeId;
    @Enumerated(EnumType.STRING) private UpdateStrategy strategy;
    private UUID targetArtifactVersionId;
    @Enumerated(EnumType.STRING) private PlanItemStatus status;
    private String appliedFromVersion;
    @Column(length = 4000) private String errorMessage;
    private Instant selectedAt;

    protected PlanItem() { }

    public PlanItem(UUID updatePlanId, UUID findingId, UUID occurrenceId, UUID targetProjectId,
                    UUID changePointTreeNodeId, UpdateStrategy strategy, UUID targetArtifactVersionId) {
        this.id = UUID.randomUUID(); this.updatePlanId = updatePlanId; this.findingId = findingId;
        this.occurrenceId = occurrenceId; this.targetProjectId = targetProjectId;
        this.changePointTreeNodeId = changePointTreeNodeId; this.strategy = strategy;
        this.targetArtifactVersionId = targetArtifactVersionId; this.status = PlanItemStatus.PLANNED;
        this.selectedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUpdatePlanId() { return updatePlanId; }
    public UUID getFindingId() { return findingId; }
    public UUID getOccurrenceId() { return occurrenceId; }
    public UUID getTargetProjectId() { return targetProjectId; }
    public UUID getChangePointTreeNodeId() { return changePointTreeNodeId; }
    public UpdateStrategy getStrategy() { return strategy; }
    public UUID getTargetArtifactVersionId() { return targetArtifactVersionId; }
    public PlanItemStatus getStatus() { return status; }
    public String getAppliedFromVersion() { return appliedFromVersion; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getSelectedAt() { return selectedAt; }

    public void markApplied(String currentVersion) { this.appliedFromVersion = currentVersion; this.errorMessage = null; this.status = PlanItemStatus.APPLIED; }
    public void markFailed(String errorMessage) { this.errorMessage = errorMessage; this.status = PlanItemStatus.FAILED; }
    public void resetToPlanned() { this.errorMessage = null; this.status = PlanItemStatus.PLANNED; }

    public void change(UUID targetProjectId, UUID changePointTreeNodeId,
                       UpdateStrategy strategy, UUID targetArtifactVersionId) {
        this.targetProjectId = targetProjectId; this.changePointTreeNodeId = changePointTreeNodeId;
        this.strategy = strategy; this.targetArtifactVersionId = targetArtifactVersionId;
        this.errorMessage = null; this.status = PlanItemStatus.PLANNED; this.selectedAt = Instant.now();
    }
}
