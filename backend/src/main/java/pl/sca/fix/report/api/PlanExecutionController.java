package pl.sca.fix.report.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.report.api.PlanExecutionDtos.ExecutionStartedDto;
import pl.sca.fix.report.api.PlanExecutionDtos.StartExecutionRequest;
import pl.sca.fix.report.application.PlanExecutionService;

@RestController
@RequestMapping("/api/update-plans/{planId}")
public class PlanExecutionController {
    private final PlanExecutionService service;
    public PlanExecutionController(PlanExecutionService service) { this.service = service; }
    @PostMapping("/apply") @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionStartedDto apply(@PathVariable UUID planId, @RequestBody StartExecutionRequest request) {
        return service.startApply(planId, request != null && request.diffConfirmed());
    }
    @PostMapping("/commit") @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionStartedDto commit(@PathVariable UUID planId) { return service.startCommit(planId); }
    @PostMapping("/revert") @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionStartedDto revert(@PathVariable UUID planId) { return service.startRevert(planId); }
}
