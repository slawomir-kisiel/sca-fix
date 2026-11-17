package pl.sca.fix.report.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.report.domain.PlanPreparationRun;

public interface PlanPreparationRunRepository extends JpaRepository<PlanPreparationRun, UUID> { }
