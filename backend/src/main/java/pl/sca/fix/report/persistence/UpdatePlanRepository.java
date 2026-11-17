package pl.sca.fix.report.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.report.domain.UpdatePlan;
import pl.sca.fix.report.domain.UpdatePlanStatus;

public interface UpdatePlanRepository extends JpaRepository<UpdatePlan, UUID> {
    Optional<UpdatePlan> findByActiveTrue();
    Optional<UpdatePlan> findByRepositoryIdAndActiveTrue(UUID repositoryId);
    List<UpdatePlan> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
    List<UpdatePlan> findAllByOrderByCreatedAtDesc();
    long countByStatus(UpdatePlanStatus status);
}
