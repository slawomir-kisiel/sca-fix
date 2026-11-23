package pl.sca.fix.uibackend.workspace;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/projects")
public class ProjectScopeController {
    private final ProjectScopeService service;
    public ProjectScopeController(ProjectScopeService service) { this.service = service; }

    @GetMapping
    public List<ProjectScopeService.ProjectView> discover(@PathVariable UUID workspaceId) {
        return service.discover(workspaceId);
    }

    @PutMapping("/scope")
    public List<ProjectScopeService.ProjectView> updateScope(@PathVariable UUID workspaceId,
                                                              @Valid @RequestBody ProjectScopeService.ScopeRequest request) {
        return service.updateScope(workspaceId, request);
    }
}
