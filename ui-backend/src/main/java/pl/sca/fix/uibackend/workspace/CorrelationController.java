package pl.sca.fix.uibackend.workspace;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/correlation")
public class CorrelationController {
    private final CorrelationService service;
    public CorrelationController(CorrelationService service) { this.service = service; }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CorrelationJob start(@PathVariable UUID workspaceId) { return service.start(workspaceId); }
    @GetMapping("/jobs/{jobId}")
    public CorrelationJob get(@PathVariable UUID workspaceId, @PathVariable UUID jobId) { return service.get(jobId); }
}
