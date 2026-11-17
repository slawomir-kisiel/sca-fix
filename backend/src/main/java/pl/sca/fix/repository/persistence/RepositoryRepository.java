package pl.sca.fix.repository.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.repository.domain.Repository;

public interface RepositoryRepository extends JpaRepository<Repository, UUID> {
    boolean existsByClonePath(String clonePath);
}
