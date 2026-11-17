package pl.sca.fix.artifact.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.artifact.domain.Artifact;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {
    Optional<Artifact> findByGroupIdAndArtifactId(String groupId, String artifactId);
    Optional<Artifact> findByProjectId(java.util.UUID projectId);
}
