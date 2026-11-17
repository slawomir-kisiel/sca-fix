package pl.sca.fix.project.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.sca.fix.project.api.ProjectDtos.ParentDto;
import pl.sca.fix.project.api.ProjectDtos.ProjectCandidateDto;
import pl.sca.fix.project.api.ProjectDtos.ProjectDiscoveryResponse;
import pl.sca.fix.project.api.ProjectDtos.ProjectSummaryDto;
import pl.sca.fix.project.api.ProjectDtos.RegisterProjectsRequest;
import pl.sca.fix.project.api.ProjectDtos.RegisterProjectsResponse;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.integration.git.GitCheckoutInspector;
import pl.sca.fix.repository.application.RepositoryNotFoundException;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final RepositoryRepository repositories;
    private final PomParser pomParser;
    private final GitCheckoutInspector git;

    public ProjectService(ProjectRepository projects, RepositoryRepository repositories, PomParser pomParser,
                          GitCheckoutInspector git) {
        this.projects = projects;
        this.repositories = repositories;
        this.pomParser = pomParser;
        this.git = git;
    }

    @Transactional(readOnly = true)
    public ProjectDiscoveryResponse discover(UUID repositoryId) {
        Repository repository = repository(repositoryId);
        Path root = Path.of(repository.getClonePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new ProjectDiscoveryException("REPOSITORY_CHECKOUT_NOT_FOUND", "Repository checkout does not exist: " + root);
        }
        verifyGitCheckout(root);
        try (var paths = Files.walk(root)) {
            List<ProjectCandidateDto> candidates = paths.filter(Files::isRegularFile)
                    .filter(path -> "pom.xml".equals(path.getFileName().toString()))
                    .filter(path -> !isIgnored(path, root))
                    .filter(path -> git.isTracked(root, path))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .map(path -> candidate(repositoryId, root, path)).toList();
            return new ProjectDiscoveryResponse(repositoryId, candidates);
        } catch (IOException exception) {
            throw new ProjectDiscoveryException("PROJECT_DISCOVERY_FAILED", "Unable to scan repository checkout", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> list(UUID repositoryId) {
        repository(repositoryId);
        return projects.findByRepositoryIdOrderByPomPath(repositoryId).stream().map(this::summary).toList();
    }

    @Transactional
    public RegisterProjectsResponse register(UUID repositoryId, RegisterProjectsRequest request) {
        Repository repository = repository(repositoryId);
        Path root = Path.of(repository.getClonePath()).toAbsolutePath().normalize();
        List<ProjectSummaryDto> registered = request.pomPaths().stream().map(pathValue -> {
            Path pom = root.resolve(pathValue).normalize();
            if (!pom.startsWith(root) || !Files.isRegularFile(pom) || !"pom.xml".equals(pom.getFileName().toString())) {
                throw new ProjectDiscoveryException("POM_PATH_INVALID", "Invalid POM path: " + pathValue);
            }
            if (!git.isTracked(root, pom)) {
                throw new ProjectDiscoveryException("POM_NOT_TRACKED", "POM is not tracked by Git: " + pathValue);
            }
            PomDescriptor descriptor = pomParser.parse(root, pom);
            if (descriptor.artifactId() == null || descriptor.artifactId().isBlank()) {
                throw new ProjectDiscoveryException("POM_ARTIFACT_ID_MISSING", "POM has no artifactId: " + pathValue);
            }
            Project project = projects.findByRepositoryIdAndPomPath(repositoryId, descriptor.pomPath())
                    .orElseGet(() -> new Project(UUID.randomUUID(), repositoryId, descriptor.artifactId(), descriptor.pomPath(),
                            descriptor.groupId(), descriptor.artifactId(), descriptor.version(), descriptor.packaging(),
                            descriptor.parent().groupId(), descriptor.parent().artifactId(), descriptor.parent().version(),
                            repository.getDefaultBranch(), repository.getBaseCommitSha()));
            return summary(projects.save(project));
        }).toList();
        return new RegisterProjectsResponse(registered);
    }

    private ProjectCandidateDto candidate(UUID repositoryId, Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        try {
            PomDescriptor descriptor = pomParser.parse(root, path);
            boolean registered = projects.findByRepositoryIdAndPomPath(repositoryId, descriptor.pomPath()).isPresent();
            ParentDto parent = new ParentDto(descriptor.parent().groupId(), descriptor.parent().artifactId(), descriptor.parent().version());
            return new ProjectCandidateDto(descriptor.pomPath(), descriptor.groupId(), descriptor.artifactId(), descriptor.version(),
                    descriptor.packaging(), parent, descriptor.modules(), registered, null);
        } catch (ProjectDiscoveryException exception) {
            return new ProjectCandidateDto(relative, null, null, null, null, null, List.of(), false, exception.getMessage());
        }
    }

    private boolean isIgnored(Path path, Path root) {
        for (Path part : root.relativize(path)) {
            String value = part.toString();
            if (".git".equals(value) || "target".equals(value) || "node_modules".equals(value)) return true;
        }
        return false;
    }

    private void verifyGitCheckout(Path root) {
        try {
            git.inspect(root);
        } catch (RuntimeException exception) {
            throw new ProjectDiscoveryException("GIT_REQUIRED", "Unable to inspect Git checkout: " + root, exception);
        }
    }

    private Repository repository(UUID id) { return repositories.findById(id).orElseThrow(() -> new RepositoryNotFoundException(id)); }

    private ProjectSummaryDto summary(Project project) {
        return new ProjectSummaryDto(project.getId(), project.getRepositoryId(), project.getName(), project.getPomPath(),
                project.getGroupId(), project.getArtifactId(), project.getVersion(), project.getPackaging(), project.getStatus().name(),
                project.getMavenAnalysisStatus().name(), project.getMavenAnalysisJobId(), project.getMavenAnalysisStartedAt(), project.getMavenAnalysisFinishedAt());
    }
}
