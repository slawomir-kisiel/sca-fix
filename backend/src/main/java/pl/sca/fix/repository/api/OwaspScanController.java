package pl.sca.fix.repository.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.repository.application.OwaspScanService;

import static pl.sca.fix.repository.api.OwaspScanDtos.*;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/owasp-scan")
public class OwaspScanController {
    private final OwaspScanService service;

    public OwaspScanController(OwaspScanService service) { this.service = service; }

    @GetMapping("/plan")
    public ScanPlanDto plan(@PathVariable UUID repositoryId) { return service.plan(repositoryId); }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanStartResponse start(@PathVariable UUID repositoryId, @RequestBody(required = false) StartScanRequest request) {
        return service.start(repositoryId, request == null ? List.of() : request.scopeIds());
    }
}
