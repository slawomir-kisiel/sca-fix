package pl.sca.fix.report.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;

@Entity
@Table(name = "update_plans")
public class UpdatePlan {
    @Id private UUID id;
    private UUID repositoryId;
    private String taskId;
    private String name;
    @Enumerated(EnumType.STRING) private UpdatePlanStatus status;
    private boolean active;
    @Column(name = "branch_name") private String branchName;
    @Column(name = "base_commit_sha") private String baseCommitSha;
    private Instant appliedAt;
    private Instant committedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected UpdatePlan() { }
    public UpdatePlan(UUID id, UUID repositoryId, String taskId, String name) {
        this.id = id; this.repositoryId = repositoryId; this.taskId = taskId; this.name = name;
        this.status = UpdatePlanStatus.PLANNED; this.active = true;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public UUID getId() { return id; }
    public UUID getRepositoryId() { return repositoryId; }
    public String getTaskId() { return taskId; }
    public String getName() { return name; }
    public UpdatePlanStatus getStatus() { return status; }
    public boolean isActive() { return active; }
    public String getBranchName() { return branchName; }
    public String getBaseCommitSha() { return baseCommitSha; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getCommittedAt() { return committedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void activate() { this.active = true; this.updatedAt = Instant.now(); }
    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public void changeStatus(UpdatePlanStatus status) {
        this.status = status; if (status != UpdatePlanStatus.PLANNED) this.active = false;
        if (status == UpdatePlanStatus.APPLIED) this.appliedAt = Instant.now();
        if (status == UpdatePlanStatus.COMMITTED) this.committedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void prepareBranch(String branchName, String baseCommitSha) {
        this.branchName = branchName; this.baseCommitSha = baseCommitSha; this.updatedAt = Instant.now();
    }
}
