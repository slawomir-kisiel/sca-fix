package pl.sca.fix.artifact.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.artifact.domain.VersionRule;

public interface VersionRuleRepository extends JpaRepository<VersionRule, UUID> {
    Optional<VersionRule> findByArtifactId(UUID artifactId);
}
