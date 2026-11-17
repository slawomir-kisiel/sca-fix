package pl.sca.fix.report.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.report.domain.PlanPreparationItem;

public interface PlanPreparationItemRepository extends JpaRepository<PlanPreparationItem, UUID> {
    List<PlanPreparationItem> findByRunIdOrderByCreatedAtAsc(UUID runId);
}
