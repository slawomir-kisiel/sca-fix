package pl.sca.fix.uibackend.workspace;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/stages")
public class StageRestartController {
    private final StageRestartService service;
    public StageRestartController(StageRestartService service) { this.service = service; }
    @PostMapping("/{stage}/restart")
    public StageRestartService.RestartResult restart(@PathVariable UUID workspaceId, @PathVariable String stage, @RequestBody RestartRequest request) {
        return service.restart(workspaceId, stage, request != null && request.confirmed());
    }
    public record RestartRequest(boolean confirmed) { }
}
