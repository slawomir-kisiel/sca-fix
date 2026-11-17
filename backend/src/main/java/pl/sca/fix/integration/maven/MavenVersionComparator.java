package pl.sca.fix.integration.maven;

import java.util.Comparator;
import java.util.Locale;

/** Compares common Maven versions without treating 4.10 as lower than 4.6. */
public final class MavenVersionComparator implements Comparator<String> {
    private MavenVersionComparator() { }

    @Override
    public int compare(String left, String right) {
        return compareVersions(left, right);
    }

    public static int compareVersions(String left, String right) {
        if (left == null) return right == null ? 0 : -1;
        if (right == null) return 1;
        String[] a = numericParts(left);
        String[] b = numericParts(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";
            Integer an = number(av); Integer bn = number(bv);
            int result = an != null && bn != null ? Integer.compare(an, bn) : av.compareToIgnoreCase(bv);
            if (result != 0) return result;
        }
        String leftQualifier = qualifier(left);
        String rightQualifier = qualifier(right);
        if (leftQualifier.isEmpty()) return rightQualifier.isEmpty() ? 0 : 1;
        if (rightQualifier.isEmpty()) return -1;
        int rank = qualifierRank(leftQualifier) - qualifierRank(rightQualifier);
        return rank != 0 ? rank : leftQualifier.compareToIgnoreCase(rightQualifier);
    }

    public static boolean sameDevelopmentLine(String current, String candidate) {
        Integer currentMajor = firstNumber(current); Integer candidateMajor = firstNumber(candidate);
        if (currentMajor == null || candidateMajor == null || !currentMajor.equals(candidateMajor)) return false;
        // In the 0.x series the minor number identifies the development line.
        // Treating every 0.x version as one line would incorrectly recommend
        // upgrades such as 0.4.x for a project currently using 0.3.x.
        if (currentMajor == 0) {
            return numericPart(current, 1).equals(numericPart(candidate, 1));
        }
        return true;
    }

    public static boolean sameQualifier(String current, String candidate) {
        return qualifier(current).equalsIgnoreCase(qualifier(candidate));
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

    public static boolean isSnapshot(String version) {
        return version != null && version.toUpperCase(Locale.ROOT).contains("SNAPSHOT");
    }

    private static Integer firstNumber(String version) { return number(part(version, 0)); }
    private static Integer numericPart(String version, int index) {
        String[] parts = numericParts(version);
        return number(index < parts.length ? parts[index] : "0");
    }
    private static String part(String version, int index) {
        if (version == null) return "";
        String[] parts = numericParts(version);
        return parts[index < parts.length ? index : 0];
    }
    private static Integer number(String value) { try { return Integer.valueOf(value); } catch (Exception ignored) { return null; } }
    private static int qualifierRank(String value) {
        if (value == null) return 0;
        value = value.toLowerCase(Locale.ROOT);
        if (value.contains("snapshot")) return -4;
        if (value.contains("alpha") || value.matches(".*(^|[-.])a[0-9].*")) return -3;
        if (value.contains("beta") || value.matches(".*(^|[-.])b[0-9].*")) return -2;
        if (value.contains("rc") || value.contains("milestone") || value.contains("m[0-9]")) return -1;
        return 1;
    }
}
