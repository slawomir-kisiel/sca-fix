package pl.sca.fix.uibackend.workspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/ui-api/workspaces")
public class GitCloneController {
    private final GitCloneService service;
    public GitCloneController(GitCloneService service) { this.service = service; }
    @PostMapping("/git/plan")
    public GitCloneService.ClonePlan plan(@Valid @RequestBody GitCloneRequest request) {
        return service.plan(request.repositoryUrl(), request.clonePath());
    }
    @PostMapping("/git")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CloneJob start(@Valid @RequestBody GitCloneRequest request) {
        return service.start(request.repositoryUrl(), request.clonePath(), request.branch());
    }
    @GetMapping("/git/jobs/{jobId}")
    public CloneJob job(@PathVariable UUID jobId) { return service.get(jobId); }
    public record GitCloneRequest(@NotBlank String repositoryUrl, String clonePath, String branch) {}
}

