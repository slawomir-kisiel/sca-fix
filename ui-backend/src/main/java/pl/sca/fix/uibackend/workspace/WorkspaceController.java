package pl.sca.fix.uibackend.workspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/ui-api/workspaces")
public class WorkspaceController {
    private final WorkspaceService service;
    public WorkspaceController(WorkspaceService service) { this.service = service; }
    @PostMapping("/local") @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse createLocal(@Valid @RequestBody CreateLocalWorkspaceRequest request) {
        return service.createLocal(request.repositoryPath());
    }
    @GetMapping("/{id}") public WorkspaceResponse get(@PathVariable UUID id) { return service.get(id); }
    public record CreateLocalWorkspaceRequest(@NotBlank String repositoryPath) {}
}

