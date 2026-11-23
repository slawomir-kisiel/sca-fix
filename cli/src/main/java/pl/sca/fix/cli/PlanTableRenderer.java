package pl.sca.fix.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.workspace.WorkspaceState;
import pl.sca.fix.core.pipeline.ScaFixPipeline;

/** Renders a fixed-width plan table with continuous ASCII borders. */
public final class PlanTableRenderer {
    private static final int DEPENDENCY_LIMIT = 60;
    private static final String[] HEADERS = {"Dependency", "Current", "Target", "Project", "Strategy", "Status"};

    public static String renderStageHeader(ScaFixPipeline.StageProgress progress) {
        Objects.requireNonNull(progress, "progress must not be null");
        String[] headers = {"Stage: " + progress.number() + " of " + progress.total(),
                progress.stage().name(), stageDescription(progress.stage())};
        int[] widths = {headers[0].length(), headers[1].length(), headers[2].length()};
        StringBuilder result = new StringBuilder();
        border(result, widths);
        row(result, headers, widths);
        border(result, widths);
        return result.toString();
    }

    private static String stageDescription(WorkspaceState.Stage stage) {
        return switch (stage) {
            case DISCOVERY -> "Finds tracked Maven POM files and project metadata";
            case MAVEN_ANALYSIS -> "Generates effective POMs and dependency trees";
            case REPORT -> "Loads and normalizes the security report";
            case CORRELATION -> "Matches findings with dependency tree occurrences";
            case OVERRIDE_REVIEW -> "Checks whether CVE overrides are still necessary";
            case PLANNING -> "Builds PlanItems with recommended dependency updates";
            case APPLY -> "Applies selected plan changes to source POM files";
            case PUBLISH -> "Commits and pushes applied changes when requested";
            case SUMMARY -> "Persists and presents the final pipeline summary";
        };
    }

    public String render(List<PlanRow> rows) {
        List<PlanRow> safeRows = rows == null ? List.of() : List.copyOf(rows);
        List<String[]> values = new ArrayList<>();
        for (PlanRow row : safeRows) {
            Objects.requireNonNull(row, "rows must not contain null");
            values.add(new String[]{shortenDependency(row.dependency()), text(row.current()), text(row.target()),
                    text(row.project()), text(row.strategy()), text(row.status())});
        }
        int[] widths = widths(values);
        StringBuilder result = new StringBuilder();
        border(result, widths);
        row(result, HEADERS, widths);
        border(result, widths);
        for (String[] value : values) row(result, value, widths);
        border(result, widths);
        return result.toString();
    }

    public String renderWarnings(List<WorkspaceState.Issue> warnings) {
        List<WorkspaceState.Issue> safe = warnings == null ? List.of() : warnings.stream()
                .filter(Objects::nonNull).toList();
        if (safe.isEmpty()) return "";
        String[] headers = {"STAGE", "reason", "message"};
        List<String[]> values = safe.stream()
                .map(issue -> new String[]{text(issue.stage()), text(issue.reason()), shortenMessage(issue.message())})
                .toList();
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) widths[i] = headers[i].length();
        for (String[] value : values) for (int i = 0; i < widths.length; i++)
            widths[i] = Math.max(widths[i], value[i].length());
        StringBuilder result = new StringBuilder("Warnings:\n");
        border(result, widths);
        row(result, headers, widths);
        border(result, widths);
        for (String[] value : values) row(result, value, widths);
        border(result, widths);
        return result.toString();
    }

    public String shortenDependency(String dependency) {
        String value = text(dependency);
        if (value.length() <= DEPENDENCY_LIMIT) return value;
        int separator = value.indexOf(':');
        if (separator < 1 || separator == value.length() - 1) return value;
        String group = value.substring(0, separator);
        String artifact = value.substring(separator + 1);
        int groupLimit = DEPENDENCY_LIMIT - artifact.length() - 1;
        if (groupLimit >= group.length()) return value;
        if (groupLimit <= 0) return group.substring(0, Math.max(0, DEPENDENCY_LIMIT - 1)) + "…" + ":" + artifact;
        String abbreviated = abbreviateGroup(group, groupLimit);
        return abbreviated + ":" + artifact;
    }

    private static String abbreviateGroup(String group, int limit) {
        String[] parts = group.split("\\.", -1);
        for (int abbreviatedParts = 1; abbreviatedParts <= parts.length; abbreviatedParts++) {
            StringBuilder candidate = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) candidate.append('.');
                candidate.append(i < abbreviatedParts ? firstCharacter(parts[i]) : parts[i]);
            }
            if (candidate.length() <= limit) return candidate.toString();
        }
        if (limit <= 1) return "…";
        return group.substring(0, limit - 1) + "…";
    }

    private static int[] widths(List<String[]> rows) {
        int[] result = new int[HEADERS.length];
        for (int i = 0; i < HEADERS.length; i++) result[i] = HEADERS[i].length();
        for (String[] row : rows) for (int i = 0; i < result.length; i++) result[i] = Math.max(result[i], row[i].length());
        return result;
    }

    private static void border(StringBuilder result, int[] widths) {
        result.append('+');
        for (int width : widths) result.append("-".repeat(width + 2)).append('+');
        result.append('\n');
    }

    private static void row(StringBuilder result, String[] values, int[] widths) {
        result.append('|');
        for (int i = 0; i < values.length; i++) {
            result.append(' ').append(values[i]);
            result.append(" ".repeat(widths[i] - values[i].length() + 1)).append('|');
        }
        result.append('\n');
    }

    private static String firstCharacter(String value) { return value.isEmpty() ? value : value.substring(0, 1); }
    private static String text(String value) { return value == null ? "" : value; }
    private static String shortenMessage(String value) {
        String text = text(value);
        return text.length() <= 512 ? text : text.substring(0, 509) + "...";
    }

    public record PlanRow(String dependency, String current, String target, String project,
                          String strategy, String status) { }
}
