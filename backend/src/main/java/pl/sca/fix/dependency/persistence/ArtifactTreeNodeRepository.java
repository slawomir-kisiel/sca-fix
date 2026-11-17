package pl.sca.fix.dependency.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.dependency.domain.ArtifactTreeNode;

public interface ArtifactTreeNodeRepository extends JpaRepository<ArtifactTreeNode, UUID> {
    List<ArtifactTreeNode> findByProjectIdOrderByDepthAsc(UUID projectId);
    List<ArtifactTreeNode> findByProjectIdAndPreviousNodeIdIsNull(UUID projectId);
    List<ArtifactTreeNode> findByPreviousNodeId(UUID previousNodeId);
    List<ArtifactTreeNode> findByArtifactVersionIdIn(List<UUID> artifactVersionIds);
    void deleteByProjectId(UUID projectId);
}
