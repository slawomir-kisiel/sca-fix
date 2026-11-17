package pl.sca.fix.job.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.job.domain.JobOutputLine;

public interface JobOutputLineRepository extends JpaRepository<JobOutputLine, Long> {
    List<JobOutputLine> findByJobIdAndSequenceNoGreaterThanOrderBySequenceNo(UUID jobId, long sequenceNo);
}
