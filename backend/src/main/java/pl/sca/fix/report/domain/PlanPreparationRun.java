package pl.sca.fix.report.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_preparation_runs")
public class PlanPreparationRun {
    @Id private UUID id;
    private UUID reportId;
    private UUID updatePlanId;
    @Enumerated(EnumType.STRING) private PlanPreparationRunStatus status;
    private Instant createdAt;
    private Instant completedAt;

    protected PlanPreparationRun() { }
    public PlanPreparationRun(UUID reportId, UUID updatePlanId) {
        this.id = UUID.randomUUID(); this.reportId = reportId; this.updatePlanId = updatePlanId;
        this.status = PlanPreparationRunStatus.RUNNING; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getReportId() { return reportId; }
    public UUID getUpdatePlanId() { return updatePlanId; }
    public PlanPreparationRunStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void complete() { status = PlanPreparationRunStatus.COMPLETED; completedAt = Instant.now(); }
}
