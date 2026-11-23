package pl.sca.fix.uibackend.workspace;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/occurrences/{occurrenceId}/strategy")
public class ChangeStrategyController {
    private final ChangeStrategyService service;
    public ChangeStrategyController(ChangeStrategyService service) { this.service = service; }
    @GetMapping public ChangeStrategyService.StrategyOptions options(@PathVariable UUID workspaceId, @PathVariable UUID occurrenceId) { return service.options(workspaceId, occurrenceId); }
    @PutMapping public ChangeStrategyService.StrategyDecision save(@PathVariable UUID workspaceId, @PathVariable UUID occurrenceId, @RequestBody ChangeStrategyService.StrategyRequest request) { return service.save(workspaceId, occurrenceId, request); }
}
