package pl.sca.fix.history.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.history.api.HistoryDtos.DashboardDto;
import pl.sca.fix.history.application.HistoryService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final HistoryService service;
    public DashboardController(HistoryService service) { this.service = service; }

    @GetMapping
    public DashboardDto dashboard() { return service.dashboard(); }
}
