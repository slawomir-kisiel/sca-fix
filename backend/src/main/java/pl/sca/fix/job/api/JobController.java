package pl.sca.fix.job.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobOutputLine;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private static final ScheduledExecutorService STREAM_EXECUTOR = Executors.newScheduledThreadPool(4);
    private final JobService service;

    public JobController(JobService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobResponse create(@RequestBody CreateJobRequest request) {
        return JobResponse.from(service.create(request.jobType()));
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return JobResponse.from(service.get(id));
    }

    @GetMapping("/{id}/output")
    public List<OutputResponse> output(@PathVariable UUID id,
                                       @RequestParam(defaultValue = "0") long afterSequence) {
        return service.output(id, afterSequence).stream().map(OutputResponse::from).toList();
    }

    @GetMapping(value = "/{id}/output/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter outputStream(@PathVariable UUID id,
                                   @RequestParam(defaultValue = "0") long afterSequence,
                                   @org.springframework.web.bind.annotation.RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        service.get(id);
        long initial = parseSequence(lastEventId, afterSequence);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicLong cursor = new AtomicLong(initial);
        ScheduledFuture<?> poller = STREAM_EXECUTOR.scheduleAtFixedRate(() -> pollOutput(id, emitter, cursor), 0, 300, java.util.concurrent.TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> poller.cancel(false));
        emitter.onTimeout(() -> poller.cancel(false));
        emitter.onError(error -> poller.cancel(false));
        return emitter;
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancel(@PathVariable UUID id) {
        return JobResponse.from(service.cancel(id));
    }

    private void pollOutput(UUID id, SseEmitter emitter, AtomicLong cursor) {
        try {
            for (JobOutputLine line : service.output(id, cursor.get())) {
                emitter.send(SseEmitter.event().id(String.valueOf(line.getSequenceNo())).name("job-output").data(OutputResponse.from(line)));
                cursor.set(line.getSequenceNo());
            }
            Job job = service.get(id);
            if (job.getStatus() == pl.sca.fix.job.domain.JobStatus.SUCCESS
                    || job.getStatus() == pl.sca.fix.job.domain.JobStatus.FAILED
                    || job.getStatus() == pl.sca.fix.job.domain.JobStatus.CANCELLED) {
                emitter.send(SseEmitter.event().name("job-complete").data(JobResponse.from(job)));
                emitter.complete();
            }
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private long parseSequence(String lastEventId, long fallback) {
        try { return lastEventId == null ? fallback : Long.parseLong(lastEventId); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    public record CreateJobRequest(String jobType) { }

    public record JobResponse(UUID jobId, String jobType, String status, String stage,
                              Instant createdAt, Instant startedAt, Instant finishedAt,
                              Integer exitCode, String errorCode, String errorMessage) {
        static JobResponse from(Job job) {
            return new JobResponse(job.getId(), job.getJobType(), job.getStatus().name(), job.getStage(),
                    job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(), job.getExitCode(),
                    job.getErrorCode(), job.getErrorMessage());
        }
    }

    public record OutputResponse(long sequenceNo, String stream, String content, Instant emittedAt) {
        static OutputResponse from(JobOutputLine line) {
            return new OutputResponse(line.getSequenceNo(), line.getStream(), line.getContent(), line.getEmittedAt());
        }
    }
}
