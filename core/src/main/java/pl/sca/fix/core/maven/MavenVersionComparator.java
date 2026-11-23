package pl.sca.fix.core.maven;

import java.util.Comparator;
import java.util.Locale;

/** Deterministic comparator for common Maven versions. */
public final class MavenVersionComparator implements Comparator<String> {
    @Override public int compare(String left, String right) { return compareVersions(left, right); }

    public static int compareVersions(String left, String right) {
        if (left == null) return right == null ? 0 : -1;
        if (right == null) return 1;
        String[] a = numericParts(left), b = numericParts(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            String av = i < a.length ? a[i] : "0", bv = i < b.length ? b[i] : "0";
            Integer an = number(av), bn = number(bv);
            int result = an != null && bn != null ? Integer.compare(an, bn) : av.compareToIgnoreCase(bv);
            if (result != 0) return result;
        }
        String leftQualifier = qualifier(left), rightQualifier = qualifier(right);
        if (leftQualifier.isEmpty()) return rightQualifier.isEmpty() ? 0 : 1;
        if (rightQualifier.isEmpty()) return -1;
        int rank = qualifierRank(leftQualifier) - qualifierRank(rightQualifier);
        return rank != 0 ? rank : leftQualifier.compareToIgnoreCase(rightQualifier);
    }

    public static boolean isSnapshot(String version) {
        return version != null && version.toUpperCase(Locale.ROOT).contains("SNAPSHOT");
    }

    public static String qualifier(String version) {
        if (version == null) return "";
        int separator = version.indexOf('-');
        return separator < 0 ? "" : version.substring(separator + 1);
    }

    private static String[] numericParts(String version) {
        String core = version;
        int separator = version.indexOf('-');
        if (separator >= 0) core = version.substring(0, separator);
        return core.split("\\.");
    }

    private static Integer number(String value) {
        try { return Integer.valueOf(value); } catch (Exception ignored) { return null; }
    }

    private static int qualifierRank(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("snapshot")) return -4;
        if (lower.contains("alpha") || lower.matches(".*(^|[-.])a[0-9].*")) return -3;
        if (lower.contains("beta") || lower.matches(".*(^|[-.])b[0-9].*")) return -2;
        if (lower.contains("rc") || lower.contains("milestone") || lower.matches(".*(^|[-.])m[0-9].*")) return -1;
        return 1;
    }
}
