package pl.sca.fix.uibackend.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.plan.PlanDocument;
import pl.sca.fix.core.plan.PlanDocumentJsonCodec;
import pl.sca.fix.core.plan.PlanItem;
import pl.sca.fix.core.plan.PlanArtifact;
import pl.sca.fix.core.plan.PlanProject;
import pl.sca.fix.core.plan.PlanRepository;
import pl.sca.fix.core.report.UpdateLevel;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {
    @Mock WorkspaceRepository workspaces;
    @Mock GitPort git;
    @Test void updatesPlanItem(@TempDir Path temp) throws Exception {
        UUID id = UUID.randomUUID(); when(workspaces.findById(id)).thenReturn(java.util.Optional.of(new WorkspaceEntity(id, temp.toString(), "READY", Instant.now(), Instant.now())));
        Path reports = temp.resolve("target/scafix/reports"); Files.createDirectories(reports);
        PlanItem item = new PlanItem("item-1", new PlanProject("g", "p", "1", "pom.xml"), new PlanArtifact("g", "lib"), "1", "2", "PROPERTY", null, "sha", "commit", "PLANNED", List.of(), List.of());
        PlanDocument plan = new PlanDocument(1, "plan-1", Instant.now(), new PlanRepository(temp.toString(), "commit"), UpdateLevel.MINOR, List.of(item), List.of(), List.of(), "PLANNED");
        Files.write(reports.resolve("plan.json"), new PlanDocumentJsonCodec().write(plan));
        PlanDocument result = new PlanService(workspaces).updateItem(id, "plan-1", "item-1", new PlanService.PlanItemRequest("DIRECT_DEPENDENCY", "3", "SKIPPED"));
        assertEquals("3", result.items().getFirst().targetVersion()); assertEquals("SKIPPED", result.items().getFirst().status());
    }

    @Test void previewsPlanWithoutChangingPom(@TempDir Path temp) throws Exception {
        UUID id = UUID.randomUUID(); when(workspaces.findById(id)).thenReturn(java.util.Optional.of(new WorkspaceEntity(id, temp.toString(), "READY", Instant.now(), Instant.now())));
        Path reports = temp.resolve("target/scafix/reports"); Files.createDirectories(reports);
        Files.writeString(temp.resolve("pom.xml"), "<project>\n  <modelVersion>4.0.0</modelVersion>\n  <dependencies>\n    <dependency>\n      <groupId>g</groupId><artifactId>lib</artifactId><version>1.0</version>\n    </dependency>\n  </dependencies>\n</project>\n");
        PlanItem item = new PlanItem("item-1", new PlanProject("g", "p", "1", "pom.xml"), new PlanArtifact("g", "lib"), "1.0", "2.0", "DIRECT_DEPENDENCY", null, "sha", "commit", "PLANNED", List.of(), List.of());
        PlanDocument plan = new PlanDocument(1, "plan-1", Instant.now(), new PlanRepository(temp.toString(), "commit"), UpdateLevel.MINOR, List.of(item), List.of(), List.of(), "PLANNED");
        Files.write(reports.resolve("plan.json"), new PlanDocumentJsonCodec().write(plan));
        PlanDiffService.PlanDiff result = new PlanDiffService(workspaces).get(id, "plan-1");
        assertTrue(result.items().getFirst().changed());
        assertTrue(result.items().getFirst().diff().contains("-      <groupId>g</groupId><artifactId>lib</artifactId><version>1.0</version>"));
        assertTrue(Files.readString(temp.resolve("pom.xml")).contains("<version>1.0</version>"));
    }
}
