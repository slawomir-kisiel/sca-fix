package pl.sca.fix.project.application;

import java.util.List;

public record PomDescriptor(String pomPath, String groupId, String artifactId, String version,
                            String packaging, ParentCoordinates parent, List<String> modules) {
    public record ParentCoordinates(String groupId, String artifactId, String version) { }
}
