package pl.sca.fix.integration.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MavenDependencyTreeParser {
    private static final Pattern COORDINATES = Pattern.compile("([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([^:\\s]+):([^:\\s]+)(?::([^:\\s]+))?");

    public List<MavenCoordinate> parse(String output) {
        List<MavenCoordinate> result = new ArrayList<>();
        if (output == null) return result;
        boolean treeSection = false;
        for (String raw : output.split("\\R")) {
            if (raw.startsWith("[INFO]") && raw.contains("---") && raw.contains(":tree") && raw.contains("---")) {
                treeSection = true;
                continue;
            }
            if (!treeSection || !raw.startsWith("[INFO]")) continue;
            String line = raw.replaceFirst("^\\[INFO\\]\\s*", "");
            if (line.contains("BUILD ") || line.startsWith("---")) break;
            Matcher matcher = COORDINATES.matcher(line);
            if (!matcher.find()) continue;
            String scope = matcher.group(5) == null ? null : matcher.group(5);
            int depth = treeDepth(line, matcher.start());
            result.add(new MavenCoordinate(matcher.group(1), matcher.group(2), matcher.group(4), scope, depth));
        }
        return result;
    }

    private int treeDepth(String line, int coordinateStart) {
        String prefix = line.substring(0, coordinateStart);
        if (prefix.isBlank()) return 0;
        int depth = 1;
        for (int index = 0; index + 2 < prefix.length(); index++) {
            if (prefix.startsWith("|  ", index)) depth++;
        }
        return depth;
    }
}
