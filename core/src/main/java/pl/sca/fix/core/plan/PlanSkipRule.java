package pl.sca.fix.core.plan;

import java.util.List;
import java.util.Objects;

/** A rule that prevents applying a plan item for a matching library coordinate. */
public record PlanSkipRule(String groupId, String artifactId, String versionPrefix) {
    public PlanSkipRule {
        groupId = required(groupId, "groupId");
        artifactId = optional(artifactId);
        versionPrefix = optional(versionPrefix);
        if (artifactId == null && versionPrefix != null) {
            throw new IllegalArgumentException("Version requires artifactId");
        }
    }

    public static PlanSkipRule parse(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("Skip descriptor must not be blank");
        }
        String[] parts = descriptor.trim().split(":", -1);
        if (parts.length < 1 || parts.length > 3 || java.util.Arrays.stream(parts).anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Skip descriptor must be groupId, groupId:artifactId or groupId:artifactId:version");
        }
        return switch (parts.length) {
            case 1 -> new PlanSkipRule(parts[0], null, null);
            case 2 -> new PlanSkipRule(parts[0], parts[1], null);
            case 3 -> new PlanSkipRule(parts[0], parts[1], parts[2]);
            default -> throw new IllegalArgumentException("Unsupported skip descriptor: " + descriptor);
        };
    }

    public static List<PlanSkipRule> parseAll(List<String> descriptors) {
        return descriptors == null ? List.of() : descriptors.stream().filter(Objects::nonNull)
                .filter(value -> !value.isBlank()).map(PlanSkipRule::parse).distinct().toList();
    }

    public boolean matches(PlanItem item) {
        if (item == null || item.artifact() == null) return false;
        if (!groupId.equals(item.artifact().groupId())) return false;
        if (artifactId != null && !artifactId.equals(item.artifact().artifactId())) return false;
        return versionPrefix == null || (item.currentVersion() != null && item.currentVersion().startsWith(versionPrefix));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
