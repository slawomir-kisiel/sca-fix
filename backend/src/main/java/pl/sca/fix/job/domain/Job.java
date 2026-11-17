package pl.sca.fix.job.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {
    @Id
    private UUID id;
    private String jobType;
    @Enumerated(EnumType.STRING)
    private JobStatus status;
    private String stage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer exitCode;
    private String errorCode;
    private String errorMessage;

    protected Job() { }

    public Job(UUID id, String jobType) {
        this.id = id;
        this.jobType = jobType;
        this.status = JobStatus.QUEUED;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getJobType() { return jobType; }
    public JobStatus getStatus() { return status; }
    public String getStage() { return stage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Integer getExitCode() { return exitCode; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }

    public void start(String stage) {
        this.status = JobStatus.RUNNING;
        this.stage = stage;
        this.startedAt = Instant.now();
    }

    public void finish(JobStatus status, Integer exitCode, String errorCode, String errorMessage) {
        this.status = status;
        this.exitCode = exitCode;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }
}
