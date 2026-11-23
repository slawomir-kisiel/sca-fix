package pl.sca.fix.core.report;

import java.util.ArrayList;
import java.util.List;

final class VersionCompatibility {
    private VersionCompatibility() { }

    static boolean matches(String reported, String resolved, UpdateLevel level) {
        if (reported == null || reported.isBlank()) return true;
        if (resolved == null || resolved.isBlank()) return false;
        if (level == null) level = UpdateLevel.MINOR;
        List<Integer> expected = numericParts(reported);
        List<Integer> actual = numericParts(resolved);
        if (expected.isEmpty() || actual.isEmpty()) return reported.equals(resolved);
        return switch (level) {
            case MAJOR -> true;
            case MINOR -> expected.get(0).equals(actual.get(0));
            case PATCH -> expected.get(0).equals(actual.get(0))
                    && value(expected, 1) == value(actual, 1);
        };
    }

    private static int value(List<Integer> values, int index) { return index < values.size() ? values.get(index) : 0; }

    private static List<Integer> numericParts(String value) {
        List<Integer> parts = new ArrayList<>();
        for (String part : value.split("[.-]")) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) break;
            try { parts.add(Integer.parseInt(part)); } catch (NumberFormatException ignored) { break; }
        }
        return parts;
    }
}
