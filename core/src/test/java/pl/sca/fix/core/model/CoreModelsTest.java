package pl.sca.fix.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreModelsTest {
    private static final ArtifactCoordinate COORDINATE = new ArtifactCoordinate(" org.example ", "service", "1.2.3");

    @Test
    void artifactCoordinateNormalizesOptionalMavenFieldsAndHasValueEquality() {
        ArtifactCoordinate actual = new ArtifactCoordinate("org.example", "service", "1.2.3", null, null);

        assertEquals("jar", actual.type());
        assertEquals("", actual.classifier());
        assertEquals("org.example:service:1.2.3", actual.gav());
        assertEquals(COORDINATE, actual);
    }

    @Test
    void artifactCoordinateRejectsMissingRequiredCoordinates() {
        assertThrows(NullPointerException.class, () -> new ArtifactCoordinate(null, "service", "1"));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactCoordinate("org.example", " ", "1"));
    }

    @Test
    void projectModelCopiesAndValidatesRepositoryRelativePaths() {
        List<Path> modules = new ArrayList<>(List.of(Path.of("modules/api/pom.xml")));
        ProjectModel project = new ProjectModel(COORDINATE, " Service ", Path.of("pom.xml"), null, null, modules);
        modules.clear();

        assertEquals("Service", project.name());
        assertEquals("jar", project.packaging());
        assertEquals(1, project.modulePomPaths().size());
        assertThrows(UnsupportedOperationException.class, () -> project.modulePomPaths().add(Path.of("pom.xml")));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectModel(COORDINATE, Path.of("../../outside/pom.xml")));
    }

    @Test
    void projectScopeKeepsAnalysisAndEditableSetsDistinctAndImmutable() {
        ProjectModel project = new ProjectModel(COORDINATE, Path.of("pom.xml"));
        ProjectScope scope = new ProjectScope(List.of(project), List.of());

        assertEquals(List.of(project), scope.analysisProjects());
        assertTrue(scope.editableProjects().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> scope.analysisProjects().clear());
        assertThrows(IllegalArgumentException.class, () -> new ProjectScope(List.of(project, project), List.of()));
    }

    @Test
    void repositoryAndWorkspaceNormalizeTheirDirectories() {
        RepositoryModel repository = new RepositoryModel(Path.of(".", "checkout"), " https://example.test/repo.git ", "abc123");
        WorkspaceModel workspace = new WorkspaceModel(" ws-1 ", Path.of(".", "target", "ws"), repository,
                new ProjectScope(List.of(), List.of()));

        assertTrue(repository.rootDirectory().isAbsolute());
        assertEquals("https://example.test/repo.git", repository.remoteUrl());
        assertEquals("ws-1", workspace.workspaceId());
        assertTrue(workspace.workspaceDirectory().isAbsolute());
    }
}
