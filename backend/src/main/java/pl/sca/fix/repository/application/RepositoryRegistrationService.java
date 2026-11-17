package pl.sca.fix.repository.application;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.sca.fix.common.process.ExternalProcessRunner;
import pl.sca.fix.integration.git.GitCheckoutInfo;
import pl.sca.fix.integration.git.GitCheckoutInspector;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.repository.api.RepositoryDtos.RegisterLocalRepositoryRequest;
import pl.sca.fix.repository.api.RepositoryDtos.RegisterGitRepositoryRequest;
import pl.sca.fix.repository.api.RepositoryDtos.RegisterRepositoryResponse;
import pl.sca.fix.repository.api.RepositoryDtos.JobReferenceDto;
import pl.sca.fix.repository.api.RepositoryDtos.RepositorySummaryDto;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;
import pl.sca.fix.workspace.application.WorkspaceConfigurationService;

@Service
public class RepositoryRegistrationService {
    private final RepositoryRepository repositories;
    private final GitCheckoutInspector git;
    private final RepositoryQueryService queries;
    private final WorkspaceConfigurationService workspace;
    private final JobService jobs;
    private final ExternalProcessRunner processRunner;

    public RepositoryRegistrationService(RepositoryRepository repositories, GitCheckoutInspector git,
                                          RepositoryQueryService queries, WorkspaceConfigurationService workspace,
                                          JobService jobs, ExternalProcessRunner processRunner) {
        this.repositories = repositories;
        this.git = git;
        this.queries = queries;
        this.workspace = workspace;
        this.jobs = jobs;
        this.processRunner = processRunner;
    }

    @Transactional
    public RepositorySummaryDto registerLocal(RegisterLocalRepositoryRequest request) {
        RepositoryRequestValidator.validateClonePath(request.clonePath());
        Path checkout = Path.of(request.clonePath().trim()).toAbsolutePath().normalize();
        if (repositories.existsByClonePath(checkout.toString())) {
            throw new RepositoryRegistrationException("REPOSITORY_ALREADY_REGISTERED",
                    "Repository is already registered: " + checkout);
        }
        GitCheckoutInfo info = git.inspect(checkout);
        Repository repository = new Repository(UUID.randomUUID(), "git", info.remoteUrl(), checkout.toString());
        repository.updateCheckoutMetadata(info.branch(), info.commitSha());
        return queries.summary(repositories.save(repository));
    }

    public RegisterRepositoryResponse startGitClone(RegisterGitRepositoryRequest request) {
        workspace.ensureDirectory();
        Path target = workspace.resolvePath(request.clonePath() == null || request.clonePath().isBlank()
                ? defaultCloneName(request.repositoryUrl()) : request.clonePath());
        validateCloneTarget(target);
        Job job = jobs.create("GIT_CLONE");
        CompletableFuture.runAsync(() -> cloneAndRegister(job.getId(), request, target));
        return new RegisterRepositoryResponse(null, new JobReferenceDto(job.getId(), job.getStatus().name()), target.toString());
    }

    private void cloneAndRegister(UUID jobId, RegisterGitRepositoryRequest request, Path target) {
        try {
            jobs.start(jobId, "GIT_CLONE");
            Files.createDirectories(target.getParent());
            List<String> command = new ArrayList<>(List.of("git", "clone"));
            if (request.branch() != null && !request.branch().isBlank()) {
                command.add("--branch");
                command.add(request.branch().trim());
            }
            command.add(request.repositoryUrl().trim());
            command.add(target.toString());
            AtomicLong sequence = new AtomicLong();
            jobs.appendCommand(jobId, sequence.incrementAndGet(), command);
            int exitCode = processRunner.run(jobId, command, target.getParent(),
                    line -> jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDOUT", line),
                    line -> jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDERR", line)).join();
            if (exitCode != 0) {
                jobs.finish(jobId, JobStatus.FAILED, exitCode, "GIT_CLONE_FAILED", "Git clone failed");
                return;
            }
            GitCheckoutInfo info = git.inspect(target);
            Repository repository = new Repository(UUID.randomUUID(), "git", info.remoteUrl(), target.toString());
            repository.updateCheckoutMetadata(info.branch(), info.commitSha());
            repositories.save(repository);
            jobs.finish(jobId, JobStatus.SUCCESS, 0, null, null);
        } catch (RuntimeException exception) {
            jobs.finish(jobId, JobStatus.FAILED, null, "GIT_CLONE_FAILED", exception.getMessage());
        } catch (Exception exception) {
            jobs.finish(jobId, JobStatus.FAILED, null, "GIT_CLONE_FAILED", exception.getMessage());
        }
    }

    private void validateCloneTarget(Path target) {
        if (repositories.existsByClonePath(target.toString())) {
            throw new RepositoryRegistrationException("REPOSITORY_ALREADY_REGISTERED",
                    "Repository is already registered: " + target);
        }
        if (Files.exists(target)) {
            try (var files = Files.list(target)) {
                if (!Files.isDirectory(target) || files.findAny().isPresent()) {
                    throw new RepositoryRegistrationException("CLONE_TARGET_NOT_EMPTY",
                            "Clone target must be an empty directory: " + target);
                }
            } catch (java.io.IOException exception) {
                throw new RepositoryRegistrationException("CLONE_TARGET_INVALID",
                        "Unable to inspect clone target: " + target);
            }
        } else if (target.getParent() == null || !Files.isDirectory(target.getParent()) || !Files.isWritable(target.getParent())) {
            throw new RepositoryRegistrationException("CLONE_TARGET_PARENT_INVALID",
                    "Clone target parent does not exist or is not writable: " + target.getParent());
        }
    }

    static String defaultCloneName(String repositoryUrl) {
        String value = repositoryUrl.trim().replaceAll("[\\\\/]+$", "");
        int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf(':'));
        String name = separator >= 0 ? value.substring(separator + 1) : value;
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }
}
