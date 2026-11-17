package pl.sca.fix.job.persistence;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobStatus;

public interface JobRepository extends JpaRepository<Job, UUID> {
    long countByStatusIn(List<JobStatus> statuses);
}
