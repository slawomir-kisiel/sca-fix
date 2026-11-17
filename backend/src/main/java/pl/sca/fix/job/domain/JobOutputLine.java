package pl.sca.fix.job.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_output_lines")
public class JobOutputLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID jobId;
    private Long sequenceNo;
    private String stream;
    private String content;
    private Instant emittedAt;

    protected JobOutputLine() { }

    public JobOutputLine(UUID jobId, long sequenceNo, String stream, String content) {
        this.jobId = jobId;
        this.sequenceNo = sequenceNo;
        this.stream = stream;
        this.content = content;
        this.emittedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getJobId() { return jobId; }
    public Long getSequenceNo() { return sequenceNo; }
    public String getStream() { return stream; }
    public String getContent() { return content; }
    public Instant getEmittedAt() { return emittedAt; }
}
