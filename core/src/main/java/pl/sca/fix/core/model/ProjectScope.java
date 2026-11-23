package pl.sca.fix.core.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Separates projects used to resolve/analyze dependencies from editable projects. */
public record ProjectScope(List<ProjectModel> analysisProjects, List<ProjectModel> editableProjects) {
    public ProjectScope {
        analysisProjects = List.copyOf(Objects.requireNonNull(analysisProjects, "analysisProjects must not be null"));
        editableProjects = List.copyOf(Objects.requireNonNull(editableProjects, "editableProjects must not be null"));
        ensureUniquePomPaths(analysisProjects, "analysisProjects");
        ensureUniquePomPaths(editableProjects, "editableProjects");
    }

    private static void ensureUniquePomPaths(List<ProjectModel> projects, String field) {
        Set<Object> pomPaths = new HashSet<>();
        for (ProjectModel project : projects) {
            Objects.requireNonNull(project, field + " must not contain null projects");
            if (!pomPaths.add(project.pomPath())) {
                throw new IllegalArgumentException(field + " contains duplicate POM path: " + project.pomPath());
            }
        }
    }
}
