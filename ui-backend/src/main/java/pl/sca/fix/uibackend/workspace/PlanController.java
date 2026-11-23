package pl.sca.fix.uibackend.workspace;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import pl.sca.fix.core.plan.PlanDocument;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/plans")
public class PlanController {
    private final PlanService service;
    private final PlanDiffService diffService;
    private final PlanApplyService applyService;
    private final GitCommitService gitCommitService;
    public PlanController(PlanService service, PlanDiffService diffService, PlanApplyService applyService, GitCommitService gitCommitService) { this.service = service; this.diffService = diffService; this.applyService = applyService; this.gitCommitService = gitCommitService; }
    @GetMapping public PlanDocument get(@PathVariable UUID workspaceId) { return service.get(workspaceId); }
    @PutMapping("/{planId}/items/{itemId}")
    public PlanDocument updateItem(@PathVariable UUID workspaceId, @PathVariable String planId, @PathVariable String itemId,
                                   @RequestBody PlanService.PlanItemRequest request) {
        return service.updateItem(workspaceId, planId, itemId, request);
    }
    @PostMapping("/{planId}/diff")
    public PlanDiffService.PlanDiff diff(@PathVariable UUID workspaceId, @PathVariable String planId) {
        return diffService.get(workspaceId, planId);
    }
    @PostMapping("/{planId}/apply")
    public PlanApplyJob apply(@PathVariable UUID workspaceId, @PathVariable String planId, @RequestBody(required = false) ApplyRequest request) {
        ApplyRequest value = request == null ? new ApplyRequest(java.util.List.of(), false) : request;
        return applyService.start(workspaceId, planId, value.itemIds(), value.retry());
    }
    @GetMapping("/apply/jobs/{jobId}")
    public PlanApplyJob applyJob(@PathVariable UUID workspaceId, @PathVariable UUID jobId) { return applyService.get(workspaceId, jobId); }
    @GetMapping("/{planId}/git-status")
    public GitCommitService.GitStatusView gitStatus(@PathVariable UUID workspaceId, @PathVariable String planId) { return gitCommitService.status(workspaceId); }
    @PostMapping("/{planId}/commit")
    public GitCommitJob commit(@PathVariable UUID workspaceId, @PathVariable String planId, @RequestBody CommitRequest request) { return gitCommitService.start(workspaceId, planId, request.message(), request.push()); }
    @GetMapping("/commit/jobs/{jobId}")
    public GitCommitJob commitJob(@PathVariable UUID workspaceId, @PathVariable UUID jobId) { return gitCommitService.get(workspaceId, jobId); }
    public record CommitRequest(String message, boolean push) { }
    public record ApplyRequest(java.util.List<String> itemIds, boolean retry) { }
}
