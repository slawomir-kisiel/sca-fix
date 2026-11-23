package pl.sca.fix.core.discovery;

/** Partially resolved parent coordinates read directly from a POM. */
public record ParentDescriptor(String groupId, String artifactId, String version, String relativePath) {
    public ParentDescriptor(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, "../pom.xml");
    }
    public ParentDescriptor {
        groupId = blankToNull(groupId);
        artifactId = blankToNull(artifactId);
        version = blankToNull(version);
        relativePath = relativePath == null || relativePath.isBlank() ? "../pom.xml" : relativePath.trim();
    }

    public boolean complete() {
        return groupId != null && artifactId != null && version != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
