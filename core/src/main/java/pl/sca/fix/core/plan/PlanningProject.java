package pl.sca.fix.core.plan;

import java.nio.file.Path;
import java.util.Objects;
import pl.sca.fix.core.maven.EffectivePom;

/** Effective-POM context required to prepare a PlanItem. */
public record PlanningProject(String projectId, Path pomPath, EffectivePom effectivePom,
                              boolean editable) {
    public PlanningProject {
        projectId = Objects.requireNonNull(projectId, "projectId").trim();
        pomPath = Objects.requireNonNull(pomPath, "pomPath");
        effectivePom = Objects.requireNonNull(effectivePom, "effectivePom");
    }
}
