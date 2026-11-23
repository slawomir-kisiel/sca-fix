package pl.sca.fix.core.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import pl.sca.fix.core.model.ProjectModel;
import pl.sca.fix.core.model.ProjectScope;

/** Deterministic result of one repository discovery operation. */
public record ProjectDiscoveryResult(List<ProjectModel> projects,
                                     ProjectScope scope,
                                     Map<Path, PomDescriptor> descriptors) {
    public ProjectDiscoveryResult {
        projects = List.copyOf(Objects.requireNonNull(projects, "projects must not be null"));
        Objects.requireNonNull(scope, "scope must not be null");
        descriptors = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(descriptors, "descriptors must not be null")));
    }
}
