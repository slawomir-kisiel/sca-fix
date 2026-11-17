package pl.sca.fix.dependency.api;

import java.util.UUID;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.dependency.api.DependencyGraphDtos.DependencyAnalysisResponse;
import pl.sca.fix.dependency.api.DependencyGraphDtos.BatchDependencyAnalysisResponse;
import pl.sca.fix.dependency.api.DependencyGraphDtos.DependencyGraphDto;
import pl.sca.fix.dependency.application.DependencyAnalysisService;
import pl.sca.fix.dependency.application.ArtifactTreeQueryService;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class DependencyGraphController {
    private final DependencyAnalysisService analysis;
    private final ArtifactTreeQueryService query;

    public DependencyGraphController(DependencyAnalysisService analysis, ArtifactTreeQueryService query) { this.analysis = analysis; this.query = query; }

    @PostMapping("/dependency-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DependencyAnalysisResponse start(@PathVariable UUID projectId) { return analysis.start(projectId); }

    @GetMapping("/dependency-graph")
    public DependencyGraphDto graph(@PathVariable UUID projectId) { return query.get(projectId); }

    @GetMapping("/dependency-graph/{artifactVersionId}/children")
    public DependencyGraphDto children(@PathVariable UUID projectId, @PathVariable UUID artifactVersionId) {
        return query.getChildren(projectId, artifactVersionId);
    }
}
