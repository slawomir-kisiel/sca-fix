package pl.sca.fix.report.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.report.domain.DependencyTreeOccurrence;

public interface DependencyTreeOccurrenceRepository extends JpaRepository<DependencyTreeOccurrence, UUID> {
    List<DependencyTreeOccurrence> findByFindingId(UUID findingId);
    void deleteByFindingId(UUID findingId);
    void deleteByProjectId(UUID projectId);
    long countByFindingId(UUID findingId);
}
