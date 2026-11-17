package pl.sca.fix.report.api;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.report.api.UpdatePlanDtos.ChangePlanStatusRequest;
import pl.sca.fix.report.api.UpdatePlanDtos.CreatePlanRequest;
import pl.sca.fix.report.api.UpdatePlanDtos.PlanDetailsDto;
import pl.sca.fix.report.api.UpdatePlanDtos.PlanSummaryDto;
import pl.sca.fix.report.application.UpdatePlanService;

@RestController
@RequestMapping("/api/update-plans")
public class UpdatePlanController {
    private final UpdatePlanService service;
    public UpdatePlanController(UpdatePlanService service) { this.service = service; }
    @GetMapping public List<PlanSummaryDto> list(@RequestParam(required = false) UUID repositoryId) { return repositoryId == null ? service.list() : service.list(repositoryId); }
    @GetMapping("/active") public ResponseEntity<Object> active(@RequestParam(required = false) UUID repositoryId) {
        PlanSummaryDto plan = repositoryId == null ? service.active() : service.active(repositoryId);
        return ResponseEntity.ok(plan == null ? NullNode.getInstance() : plan);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public PlanSummaryDto create(@RequestBody(required = false) CreatePlanRequest request) { return service.create(request); }
    @GetMapping("/{id}") public PlanDetailsDto details(@PathVariable UUID id) { return service.details(id); }
    @PutMapping("/{id}/active") public PlanSummaryDto activate(@PathVariable UUID id) { return service.activate(id); }
    @PutMapping("/{id}/status") public PlanSummaryDto status(@PathVariable UUID id, @RequestBody ChangePlanStatusRequest request) { return service.changeStatus(id, request == null ? null : request.status()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
    @DeleteMapping("/{planId}/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteItem(@PathVariable UUID planId, @PathVariable UUID itemId) { service.deleteItem(planId, itemId); }
}
