package pl.sca.fix.project.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.project.application.ProjectService;
import pl.sca.fix.project.api.ProjectDtos.ProjectDiscoveryResponse;
import pl.sca.fix.project.api.ProjectDtos.ProjectSummaryDto;
import pl.sca.fix.project.api.ProjectDtos.RegisterProjectsRequest;
import pl.sca.fix.project.api.ProjectDtos.RegisterProjectsResponse;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) { this.service = service; }

    @PostMapping("/discover")
    public ProjectDiscoveryResponse discover(@PathVariable UUID repositoryId) { return service.discover(repositoryId); }

    @GetMapping
    public List<ProjectSummaryDto> list(@PathVariable UUID repositoryId) { return service.list(repositoryId); }

    @PostMapping
    public RegisterProjectsResponse register(@PathVariable UUID repositoryId,
                                             @Valid @RequestBody RegisterProjectsRequest request) {
        return service.register(repositoryId, request);
    }
}
