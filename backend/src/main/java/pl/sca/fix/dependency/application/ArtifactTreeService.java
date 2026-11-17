package pl.sca.fix.dependency.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.sca.fix.artifact.domain.AnalysisStatus;
import pl.sca.fix.artifact.domain.Artifact;
import pl.sca.fix.artifact.domain.ArtifactVersion;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.artifact.application.ArtifactVersionWriteCoordinator;
import pl.sca.fix.dependency.domain.ArtifactTreeNode;
import pl.sca.fix.dependency.domain.DependencyRelation;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.integration.maven.MavenCoordinate;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;

@Service
public class ArtifactTreeService {
    private final ArtifactRepository artifacts;
    private final ArtifactVersionRepository versions;
    private final ArtifactTreeNodeRepository nodes;
    private final ProjectRepository projects;
    private final ArtifactVersionWriteCoordinator versionWrites;

    public ArtifactTreeService(ArtifactRepository artifacts, ArtifactVersionRepository versions,
                               ArtifactTreeNodeRepository nodes, ProjectRepository projects,
                               ArtifactVersionWriteCoordinator versionWrites) {
        this.artifacts = artifacts; this.versions = versions; this.nodes = nodes; this.projects = projects; this.versionWrites = versionWrites;
    }

    @Transactional
    public void replace(Project project, List<MavenCoordinate> coordinates, boolean ancestorsAvailable) {
        // This method is called only after a successful Maven run. An empty tree
        // is therefore a valid result and must replace the previous tree as well.
        nodes.deleteByProjectId(project.getId());
        if (coordinates.isEmpty()) return;
        Instant now = Instant.now();
        Map<String, ArtifactVersion> byCoordinate = new HashMap<>();
        for (MavenCoordinate coordinate : coordinates) {
            Artifact artifact = artifact(coordinate.groupId(), coordinate.artifactId(), project);
            ArtifactVersion version = versionWrites.execute(() -> versions.findByArtifactIdAndVersion(artifact.getId(), coordinate.version())
                    .orElseGet(() -> versions.save(new ArtifactVersion(UUID.randomUUID(), artifact.getId(), coordinate.version()))));
            version.markAnalyzed(ancestorsAvailable ? AnalysisStatus.READY : AnalysisStatus.PARTIAL, now, project.getBaseCommitSha());
            versions.save(version);
            byCoordinate.put(key(coordinate.groupId(), coordinate.artifactId(), coordinate.version()), version);
        }

        List<ArtifactTreeNode> parentNodes = parentChain(project, byCoordinate);
        UUID previous = parentNodes.isEmpty() ? null : parentNodes.get(parentNodes.size() - 1).getId();
        int parentDepth = parentNodes.size();
        ArtifactVersion rootVersion = byCoordinate.get(key(coordinates.get(0).groupId(), coordinates.get(0).artifactId(), coordinates.get(0).version()));
        ArtifactTreeNode root = new ArtifactTreeNode(project.getId(), rootVersion.getId(), previous,
                DependencyRelation.DEPENDENCY, null, parentDepth, true, false);
        nodes.save(root);
        Map<Integer, ArtifactTreeNode> branch = new HashMap<>();
        branch.put(0, root);
        for (int index = 1; index < coordinates.size(); index++) {
            MavenCoordinate coordinate = coordinates.get(index);
            ArtifactVersion target = byCoordinate.get(key(coordinate.groupId(), coordinate.artifactId(), coordinate.version()));
            if (target == null) continue;
            int treeDepth = Math.max(1, coordinate.depth());
            ArtifactTreeNode parent = branch.get(treeDepth - 1);
            if (parent == null) parent = root;
            ArtifactTreeNode node = new ArtifactTreeNode(project.getId(), target.getId(), parent.getId(),
                    DependencyRelation.DEPENDENCY, coordinate.scope(), parentDepth + treeDepth,
                    treeDepth == 1, false);
            nodes.save(node);
            branch.entrySet().removeIf(entry -> entry.getKey() >= treeDepth);
            branch.put(treeDepth, node);
        }
    }

    private List<ArtifactTreeNode> parentChain(Project project, Map<String, ArtifactVersion> known) {
        List<ParentRef> reverse = new ArrayList<>();
        String group = project.getParentGroupId(); String artifact = project.getParentArtifactId(); String version = project.getParentVersion();
        while (group != null && artifact != null && version != null) {
            reverse.add(new ParentRef(group, artifact, version));
            Project parent = projects.findFirstByGroupIdAndArtifactId(group, artifact).orElse(null);
            if (parent == null) break;
            group = parent.getParentGroupId(); artifact = parent.getParentArtifactId(); version = parent.getParentVersion();
        }
        List<ArtifactTreeNode> result = new ArrayList<>();
        UUID previous = null;
        for (int index = reverse.size() - 1; index >= 0; index--) {
            ParentRef ref = reverse.get(index);
            Artifact artifactEntity = artifact(ref.groupId(), ref.artifactId(), project);
            ArtifactVersion versionEntity = known.computeIfAbsent(key(ref.groupId(), ref.artifactId(), ref.version()), ignored ->
                    versionWrites.execute(() -> versions.findByArtifactIdAndVersion(artifactEntity.getId(), ref.version())
                            .orElseGet(() -> versions.save(new ArtifactVersion(UUID.randomUUID(), artifactEntity.getId(), ref.version())))));
            ArtifactTreeNode node = new ArtifactTreeNode(project.getId(), versionEntity.getId(), previous,
                    DependencyRelation.PARENT, null, result.size(), true, false);
            nodes.save(node); result.add(node); previous = node.getId();
        }
        return result;
    }

    private Artifact artifact(String groupId, String artifactId, Project project) {
        Project localProjectArtifact = projects.findFirstByGroupIdAndArtifactId(groupId, artifactId).orElse(null);
        UUID localProjectId = localProjectArtifact == null ?
                (Objects.equals(project.getGroupId(), groupId) && Objects.equals(project.getArtifactId(), artifactId) ? project.getId() : null) :
                localProjectArtifact.getId();
        Artifact result = artifacts.findByGroupIdAndArtifactId(groupId, artifactId).orElseGet(() ->
                new Artifact(UUID.randomUUID(), groupId, artifactId, localProjectId));
        if (localProjectId != null) result.assignProject(localProjectId);
        return artifacts.save(result);
    }

    private String key(String groupId, String artifactId, String version) { return groupId + ":" + artifactId + ":" + version; }
    private record ParentRef(String groupId, String artifactId, String version) { }
}
