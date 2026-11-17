package pl.sca.fix.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByRepositoryIdOrderByPomPath(UUID repositoryId);
    Optional<Project> findByRepositoryIdAndPomPath(UUID repositoryId, String pomPath);
    Optional<Project> findFirstByGroupIdAndArtifactId(String groupId, String artifactId);
}
