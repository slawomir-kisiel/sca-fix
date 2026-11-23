package pl.sca.fix.uibackend.workspace;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/occurrences")
public class OccurrenceReviewController {
    private final OccurrenceReviewService service;
    public OccurrenceReviewController(OccurrenceReviewService service) { this.service = service; }

    @GetMapping
    public OccurrenceReviewService.ReviewResponse get(@PathVariable UUID workspaceId) { return service.get(workspaceId); }

    @PutMapping("/{occurrenceId}/decision")
    public OccurrenceReviewService.ReviewResponse decide(@PathVariable UUID workspaceId,
                                                         @PathVariable UUID occurrenceId,
                                                         @RequestParam String action) {
        return service.decide(workspaceId, occurrenceId, action);
    }
}
