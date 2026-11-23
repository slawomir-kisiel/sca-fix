package pl.sca.fix.core.maven;

import java.time.Duration;
import java.util.Objects;

/** Independent release and snapshot TTL policy. */
public record VersionInfoTtl(Duration release, Duration snapshot) {
    public VersionInfoTtl {
        release = positive(release, "release");
        snapshot = positive(snapshot, "snapshot");
    }

    public static VersionInfoTtl defaults() { return new VersionInfoTtl(Duration.ofDays(1), Duration.ofMinutes(10)); }

    public static VersionInfoTtl parse(String value) {
        if (value == null || value.isBlank()) return defaults();
        Duration release = null, snapshot = null;
        for (String token : value.split(",")) {
            String part = token.trim();
            if (part.length() < 3) throw new IllegalArgumentException("Invalid version TTL: " + value);
            char type = Character.toUpperCase(part.charAt(0));
            Duration duration = duration(part.substring(1), value);
            if (type == 'R') {
                if (release != null) throw new IllegalArgumentException("Duplicate release TTL");
                release = duration;
            } else if (type == 'S') {
                if (snapshot != null) throw new IllegalArgumentException("Duplicate snapshot TTL");
                snapshot = duration;
            } else throw new IllegalArgumentException("Unknown version TTL type: " + type);
        }
        return new VersionInfoTtl(release == null ? defaults().release() : release,
                snapshot == null ? defaults().snapshot() : snapshot);
    }

    private static Duration duration(String value, String original) {
        if (value.length() < 2) throw new IllegalArgumentException("Invalid version TTL: " + original);
        char unit = Character.toLowerCase(value.charAt(value.length() - 1));
        long amount;
        try { amount = Long.parseLong(value.substring(0, value.length() - 1)); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid version TTL: " + original, exception); }
        if (amount <= 0) throw new IllegalArgumentException("Version TTL must be positive");
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unknown version TTL unit: " + unit);
        };
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
