package pl.sca.fix.core.report;

public enum UpdateLevel {
    PATCH, MINOR, MAJOR;

    public static UpdateLevel parse(String value) {
        if (value == null || value.isBlank()) return MINOR;
        return valueOf(value.trim().toUpperCase());
    }
}
