package pl.sca.fix.repository.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.sca.fix.integration.git.GitCheckoutInfo;
import pl.sca.fix.integration.git.GitCheckoutInspector;
import pl.sca.fix.common.process.ExternalProcessRunner;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.workspace.application.WorkspaceConfigurationService;
import pl.sca.fix.repository.api.RepositoryDtos.RegisterLocalRepositoryRequest;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;

@ExtendWith(MockitoExtension.class)
class RepositoryRegistrationServiceTest {
    @Mock RepositoryRepository repositories;
    @Mock GitCheckoutInspector git;
    @Mock RepositoryQueryService queries;
    @Mock WorkspaceConfigurationService workspace;
    @Mock JobService jobs;
    @Mock ExternalProcessRunner processRunner;

    @Test
    void registersLocalCheckoutWithGitMetadata() {
        Path checkout = Path.of("C:/work/repo").toAbsolutePath().normalize();
        Repository stored = new Repository(UUID.randomUUID(), "git", "https://git.example/repo.git", checkout.toString());
        stored.updateCheckoutMetadata("main", "abc123");
        when(repositories.existsByClonePath(checkout.toString())).thenReturn(false);
        when(git.inspect(checkout)).thenReturn(new GitCheckoutInfo("https://git.example/repo.git", "main", "abc123"));
        when(repositories.save(any(Repository.class))).thenReturn(stored);
        var expected = new pl.sca.fix.repository.api.RepositoryDtos.RepositorySummaryDto(
                stored.getId(), "repo", stored.getUrl(), stored.getClonePath(), "main", "abc123", 0, 0, 0, "READY");
        when(queries.summary(stored)).thenReturn(expected);

        var result = new RepositoryRegistrationService(repositories, git, queries, workspace, jobs, processRunner)
                .registerLocal(new RegisterLocalRepositoryRequest(checkout.toString()));

        assertThat(result).isEqualTo(expected);
    }
}
