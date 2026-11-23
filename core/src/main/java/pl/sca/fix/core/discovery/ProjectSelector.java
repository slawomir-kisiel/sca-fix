package pl.sca.fix.core.discovery;

import java.util.Objects;
import pl.sca.fix.core.model.ProjectModel;

/** Selector accepted by the CLI projects option. */
public record ProjectSelector(String value) {
    public ProjectSelector {
        Objects.requireNonNull(value, "selector must not be null");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("selector must not be blank");
        String[] parts = value.split(":", -1);
        if (!value.equals("*") && (parts.length < 2 || parts.length > 3
                || parts[0].isBlank() || parts[1].isBlank()
                || parts.length == 3 && parts[2].isBlank()
                || parts.length == 2 && !parts[1].equals("*") && parts[1].contains("*"))) {
            throw new IllegalArgumentException("unsupported Maven project selector: " + value);
        }
    }

    public boolean matches(ProjectModel project) {
        String[] parts = value.split(":", -1);
        if (value.equals("*")) return true;
        if (parts.length == 2 && parts[1].equals("*")) {
            return project.coordinate().groupId().equals(parts[0]);
        }
        if (parts.length == 2) {
            return project.coordinate().groupId().equals(parts[0])
                    && project.coordinate().artifactId().equals(parts[1]);
        }
        if (parts.length == 3) {
            return project.coordinate().groupId().equals(parts[0])
                    && project.coordinate().artifactId().equals(parts[1])
                    && project.coordinate().version().equals(parts[2]);
        }
        return false;
    }
}
