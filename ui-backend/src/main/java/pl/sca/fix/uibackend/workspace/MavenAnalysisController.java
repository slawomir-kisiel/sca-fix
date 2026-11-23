package pl.sca.fix.uibackend.workspace;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/maven-analysis")
public class MavenAnalysisController {
    private final MavenAnalysisService service;
    public MavenAnalysisController(MavenAnalysisService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<MavenAnalysisJob> start(@PathVariable UUID workspaceId,
                                        @Valid @RequestBody(required = false) AnalysisRequest request) {
        return service.start(workspaceId, request == null ? List.of() : request.projects());
    }

    @GetMapping("/jobs/{jobId}")
    public MavenAnalysisJob get(@PathVariable UUID workspaceId, @PathVariable UUID jobId) {
        return service.get(jobId);
    }

    public record AnalysisRequest(List<String> projects) {}
}
