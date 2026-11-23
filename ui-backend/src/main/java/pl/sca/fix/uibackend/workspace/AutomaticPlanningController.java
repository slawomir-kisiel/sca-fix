package pl.sca.fix.uibackend.workspace;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/planning")
public class AutomaticPlanningController {
    private final AutomaticPlanningService service;
    public AutomaticPlanningController(AutomaticPlanningService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public PlanningJob start(@PathVariable UUID workspaceId, @RequestBody(required = false) PlanningRequest request) { return service.start(workspaceId, request == null ? List.of() : request.libraries()); }
    @GetMapping("/jobs/{jobId}") public PlanningJob get(@PathVariable UUID workspaceId, @PathVariable UUID jobId) { return service.get(jobId); }
    public record PlanningRequest(List<String> libraries) {}
}
