package pl.sca.fix.report.api;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.report.api.ChangePointGraphDtos.ChangeSelectionDto;
import pl.sca.fix.report.api.ChangePointGraphDtos.ChangeSelectionRequest;
import pl.sca.fix.report.api.ChangePointGraphDtos.FocusedGraphDto;
import pl.sca.fix.report.api.ChangePointGraphDtos.TargetProjectDto;
import pl.sca.fix.report.api.StrategyDtos.StrategyOptionsDto;
import pl.sca.fix.report.application.ChangePointGraphService;
import pl.sca.fix.report.application.ChangeSelectionService;
import pl.sca.fix.report.application.StrategySelectionService;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;

@RestController
@RequestMapping("/api/findings/{findingId}/occurrences/{occurrenceId}")
public class ChangePointController {
    private final ChangePointGraphService graphs;
    private final ChangeSelectionService selections;
    private final DependencyTreeOccurrenceRepository occurrences;
    private final StrategySelectionService strategies;

    public ChangePointController(ChangePointGraphService graphs, ChangeSelectionService selections,
                                 DependencyTreeOccurrenceRepository occurrences,
                                 StrategySelectionService strategies) {
        this.graphs = graphs; this.selections = selections; this.occurrences = occurrences; this.strategies = strategies;
    }

    @GetMapping("/target-projects")
    public List<TargetProjectDto> targetProjects(@PathVariable UUID findingId, @PathVariable UUID occurrenceId) {
        var occurrence = occurrences.findById(occurrenceId).orElseThrow();
        return graphs.targetProjects(findingId, occurrenceId);
    }

    @GetMapping("/change-graph")
    public FocusedGraphDto graph(@PathVariable UUID findingId, @PathVariable UUID occurrenceId, @RequestParam UUID projectId) {
        return graphs.get(findingId, occurrenceId, projectId);
    }

    @GetMapping("/change-strategies")
    public StrategyOptionsDto strategies(@PathVariable UUID findingId, @PathVariable UUID occurrenceId,
                                         @RequestParam UUID projectId, @RequestParam UUID changePointTreeNodeId,
                                         @RequestParam(required = false) UUID targetArtifactVersionId) {
        return strategies.listForTreeNode(findingId, occurrenceId, projectId, changePointTreeNodeId, targetArtifactVersionId);
    }

    @PutMapping("/change-selection")
    public ChangeSelectionDto selection(@PathVariable UUID findingId, @RequestBody ChangeSelectionRequest request) {
        return selections.save(findingId, request);
    }
}
