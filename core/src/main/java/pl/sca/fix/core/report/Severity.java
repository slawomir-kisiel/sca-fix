package pl.sca.fix.core.report;

public enum Severity {
    UNKNOWN(0), LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

    private final int rank;

    Severity(int rank) { this.rank = rank; }

    public static Severity parse(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        try { return valueOf(value.trim().toUpperCase().replace('-', '_')); }
        catch (IllegalArgumentException ignored) { return UNKNOWN; }
    }

    public static Severity max(Severity left, Severity right) {
        return left.rank >= right.rank ? left : right;
    }
}
