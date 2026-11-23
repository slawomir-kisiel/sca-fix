package pl.sca.fix.core.maven;

import java.util.Objects;

/** Minimal numeric comparison for Maven plugin versions. */
public final class MavenPluginVersion implements Comparable<MavenPluginVersion> {
    private final String value;
    private final int[] parts;

    private MavenPluginVersion(String value) {
        this.value = value;
        String numeric = value.split("-", 2)[0];
        String[] values = numeric.split("\\.");
        parts = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            try {
                parts[index] = Integer.parseInt(values[index]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid Maven plugin version: " + value, exception);
            }
        }
    }

    public static MavenPluginVersion parse(String value) {
        Objects.requireNonNull(value, "version must not be null");
        String normalized = value.trim();
        if (!normalized.matches("\\d+(\\.\\d+)*(?:-[A-Za-z0-9.-]+)?")) {
            throw new IllegalArgumentException("Invalid Maven plugin version: " + value);
        }
        return new MavenPluginVersion(normalized);
    }

    public boolean atLeast(int major, int minor) {
        return compareTo(new MavenPluginVersion(major + "." + minor)) >= 0;
    }

    @Override
    public int compareTo(MavenPluginVersion other) {
        int length = Math.max(parts.length, other.parts.length);
        for (int index = 0; index < length; index++) {
            int left = index < parts.length ? parts[index] : 0;
            int right = index < other.parts.length ? other.parts[index] : 0;
            if (left != right) return Integer.compare(left, right);
        }
        return 0;
    }

    @Override
    public String toString() {
        return value;
    }
}
