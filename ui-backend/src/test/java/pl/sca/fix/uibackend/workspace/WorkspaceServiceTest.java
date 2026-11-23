package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitStatus;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {
    @Mock WorkspaceRepository repository;
    @Mock GitPort git;
    @InjectMocks WorkspaceService service;

    @Test
    void createsWorkspaceFromLocalGitCheckout() {
        GitStatus status = new GitStatus(true, true, "abc123", List.of(), "main");
        when(git.status(any(Path.class))).thenReturn(status);
        when(repository.save(any(WorkspaceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceResponse result = service.createLocal("checkout");

        assertEquals("checkout", result.repositoryPath().substring(result.repositoryPath().length() - 8));
        assertEquals("main", result.branch());
        assertEquals("abc123", result.commitSha());
        assertEquals("READY", result.status());
    }

    @Test
    void rejectsPathThatIsNotAGitRepository() {
        when(git.status(any(Path.class))).thenReturn(new GitStatus(false, false, null, List.of(), null));

        WorkspaceException exception = assertThrows(WorkspaceException.class,
                () -> service.createLocal("not-a-repository"));

        assertEquals("NOT_A_GIT_REPOSITORY", exception.code());
    }

    @Test
    void rejectsBlankPath() {
        WorkspaceException exception = assertThrows(WorkspaceException.class,
                () -> service.createLocal(" "));

        assertEquals("REPOSITORY_PATH_REQUIRED", exception.code());
    }
}

