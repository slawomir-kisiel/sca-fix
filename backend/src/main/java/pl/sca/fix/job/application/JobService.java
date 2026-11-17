package pl.sca.fix.job.application;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import pl.sca.fix.common.process.ExternalProcessRunner;
import pl.sca.fix.common.process.SecretRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobOutputLine;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.job.persistence.JobOutputLineRepository;
import pl.sca.fix.job.persistence.JobRepository;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final JobRepository jobs;
    private final JobOutputLineRepository outputLines;
    private final ExternalProcessRunner processRunner;
    private final SecretRedactor secretRedactor;

    public JobService(JobRepository jobs, JobOutputLineRepository outputLines,
                      ExternalProcessRunner processRunner, SecretRedactor secretRedactor) {
        this.jobs = jobs;
        this.outputLines = outputLines;
        this.processRunner = processRunner;
        this.secretRedactor = secretRedactor;
    }

    /**
     * Jobs are created by services which may themselves run in a read-only
     * transaction and start an async worker immediately afterwards. Persist
     * the job in its own transaction so the worker can always see it before
     * it starts updating status or output.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Job create(String jobType) {
        Job job = jobs.save(new Job(UUID.randomUUID(), jobType));
        log.info("Created job jobId={} type={} status={}", job.getId(), jobType, job.getStatus());
        return job;
    }

    @Transactional
    public Job start(UUID id, String stage) {
        Job job = get(id);
        job.start(stage);
        log.info("Started job jobId={} type={} stage={}", id, job.getJobType(), stage);
        return jobs.save(job);
    }

    @Transactional
    public Job finish(UUID id, JobStatus status, Integer exitCode, String errorCode, String errorMessage) {
        Job job = get(id);
        job.finish(status, exitCode, errorCode, errorMessage);
        if (status == JobStatus.FAILED) {
            log.error("Finished job jobId={} type={} status={} exitCode={} errorCode={} message={}", id, job.getJobType(), status, exitCode, errorCode, errorMessage);
        } else {
            log.info("Finished job jobId={} type={} status={} exitCode={} errorCode={}", id, job.getJobType(), status, exitCode, errorCode);
        }
        return jobs.save(job);
    }

    @Transactional(readOnly = true)
    public Job get(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<JobOutputLine> output(UUID id, long afterSequence) {
        get(id);
        return outputLines.findByJobIdAndSequenceNoGreaterThanOrderBySequenceNo(id, afterSequence);
    }

    @Transactional
    public JobOutputLine appendOutput(UUID id, long sequenceNo, String stream, String content) {
        get(id);
        log.debug("Job output jobId={} sequenceNo={} stream={}", id, sequenceNo, stream);
        return outputLines.save(new JobOutputLine(id, sequenceNo, stream, secretRedactor.redact(content)));
    }

    @Transactional
    public JobOutputLine appendCommand(UUID id, List<String> command) {
        String rendered = command.stream().map(this::renderArgument).collect(Collectors.joining(" "));
        return appendOutput(id, System.nanoTime(), "COMMAND", "$ " + rendered);
    }

    @Transactional
    public JobOutputLine appendCommand(UUID id, long sequenceNo, List<String> command) {
        String rendered = command.stream().map(this::renderArgument).collect(Collectors.joining(" "));
        return appendOutput(id, sequenceNo, "COMMAND", "$ " + rendered);
    }

    private String renderArgument(String value) {
        if (value.matches("[A-Za-z0-9_./:=@+-]+")) return value;
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    @Transactional
    public Job cancel(UUID id) {
        Job job = get(id);
        if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RUNNING) {
            log.warn("Cancelling job jobId={} type={} status={}", id, job.getJobType(), job.getStatus());
            processRunner.cancel(id);
            job.finish(JobStatus.CANCELLED, null, "JOB_CANCELLED", "Job cancelled by user");
            return jobs.save(job);
        }
        return job;
    }
}
