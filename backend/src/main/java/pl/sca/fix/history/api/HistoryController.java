package pl.sca.fix.history.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.history.api.HistoryDtos.HistoryPageDto;
import pl.sca.fix.history.application.HistoryService;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final HistoryService service;
    public HistoryController(HistoryService service) { this.service = service; }

    @GetMapping
    public HistoryPageDto history(@RequestParam(required = false) String query,
                                  @RequestParam(required = false) List<String> types,
                                  @RequestParam(required = false) List<String> statuses,
                                  @RequestParam(required = false) UUID repositoryId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "25") int size) {
        return service.history(query, types, statuses, repositoryId, from, to, page, size);
    }
}
