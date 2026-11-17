package pl.sca.fix.report.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.report.domain.PlanItem;

public interface PlanItemRepository extends JpaRepository<PlanItem, UUID> {
    List<PlanItem> findByUpdatePlanIdOrderBySelectedAtAsc(UUID updatePlanId);
    Optional<PlanItem> findByUpdatePlanIdAndOccurrenceId(UUID updatePlanId, UUID occurrenceId);
    Optional<PlanItem> findFirstByOccurrenceIdOrderBySelectedAtDesc(UUID occurrenceId);
    List<PlanItem> findByFindingIdIn(Collection<UUID> findingIds);
    long countByUpdatePlanId(UUID updatePlanId);
    void deleteByUpdatePlanId(UUID updatePlanId);
}
