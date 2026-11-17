package pl.sca.fix.artifact.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.artifact.domain.ArtifactVersion;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersion, UUID> {
    Optional<ArtifactVersion> findByArtifactIdAndVersion(UUID artifactId, String version);
    List<ArtifactVersion> findByArtifactId(UUID artifactId);
    List<ArtifactVersion> findByArtifactIdIn(List<UUID> artifactIds);
}
