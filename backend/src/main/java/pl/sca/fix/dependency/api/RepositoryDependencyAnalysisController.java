package pl.sca.fix.dependency.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.dependency.api.DependencyGraphDtos.BatchDependencyAnalysisResponse;
import pl.sca.fix.dependency.application.DependencyAnalysisService;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/projects")
public class RepositoryDependencyAnalysisController {
    private final DependencyAnalysisService analysis;

    public RepositoryDependencyAnalysisController(DependencyAnalysisService analysis) { this.analysis = analysis; }

    @PostMapping("/dependency-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BatchDependencyAnalysisResponse start(@PathVariable UUID repositoryId,
                                                  @Valid @RequestBody BatchRequest request) {
        return analysis.startBatch(repositoryId, request.projectIds());
    }

    public record BatchRequest(@NotEmpty List<UUID> projectIds) { }
}
