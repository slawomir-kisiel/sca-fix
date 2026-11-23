package pl.sca.fix.core.plan;

public enum AmbiguousStrategy {
    SKIP, COMMON_SOURCE, ALL, SHALLOWEST, PREFER_ROOT, PREFER_DIRECT;

    public static AmbiguousStrategy parse(String value) {
        if (value == null || value.isBlank()) return COMMON_SOURCE;
        // Accept the earlier typo used in some scripts while keeping the
        // documented domain value SHALLOWEST.
        if (value.trim().equalsIgnoreCase("SHALLOWLESS")) return SHALLOWEST;
        return valueOf(value.trim().toUpperCase());
    }
}
