package pl.sca.fix.uibackend.workspace;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/history")
public class HistoryController {
    private final HistoryService service;
    public HistoryController(HistoryService service) { this.service = service; }
    @GetMapping public HistoryService.HistorySnapshot get(@PathVariable UUID workspaceId) { return service.get(workspaceId); }
}
