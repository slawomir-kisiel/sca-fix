package pl.sca.fix.core.report;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.maven.DependencyTreeFileReader;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.port.FileSystemPort;

/** Manual preview of correlation between a canonical report and one dependency tree. */
public final class CorrelationPreview {
    private CorrelationPreview() { }

    public static void main(String[] args) {
        try { System.out.print(run(args)); }
        catch (Exception exception) {
            System.err.println("Correlation preview failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) {
        Map<String, String> options = options(args);
        FileSystemPort files = new NioFileSystemAdapter();
        Path reportPath = Path.of(options.get("report")).toAbsolutePath().normalize();
        Path treePath = Path.of(options.get("tree")).toAbsolutePath().normalize();
        CanonicalScanReport report = new CanonicalReportJsonCodec().read(files.readBytes(reportPath));
        ProjectTree project = new ProjectTree(options.get("project-id"), null,
                new DependencyTreeFileReader(files).read(treePath));
        CorrelationResult result = new CorrelationService().correlate(report, List.of(project),
                UpdateLevel.parse(options.get("update-level")));
        StringBuilder output = new StringBuilder();
        output.append("status: ").append(report.status()).append('\n');
        output.append("updateLevel: ").append(UpdateLevel.parse(options.get("update-level"))).append('\n');
        output.append("entries: ").append(result.entries().size()).append('\n');
        for (CorrelationEntry entry : result.entries()) {
            output.append(entry.status()).append(" ").append(entry.libraryKey())
                    .append(" occurrences=").append(entry.occurrences().size()).append('\n');
            for (DependencyTreeOccurrence occurrence : entry.occurrences()) {
                output.append("  path=").append(occurrence.pathId()).append(" node=")
                        .append(occurrence.vulnerableNodeId()).append(" version=")
                        .append(occurrence.resolvedVersion()).append(" previous=")
                        .append(occurrence.previousNodeId()).append('\n');
            }
        }
        if (!result.warnings().isEmpty()) output.append("warnings: ").append(result.warnings()).append('\n');
        Path outputPath = Path.of(options.getOrDefault("output", "correlation.json"))
                .toAbsolutePath().normalize();
        files.writeAtomically(outputPath, new CorrelationJsonCodec().write(result));
        output.append("output: ").append(outputPath).append('\n');
        return output.toString();
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) throw new IllegalArgumentException("Unknown argument: " + argument);
            String key = argument.substring(2);
            if (key.equals("verbose")) { result.put(key, "true"); continue; }
            if (index + 1 >= args.length || args[index + 1].startsWith("--"))
                throw new IllegalArgumentException("Missing value for --" + key);
            result.put(key, args[++index]);
        }
        required(result, "report"); required(result, "tree"); required(result, "project-id");
        return result;
    }

    private static void required(Map<String, String> options, String key) {
        if (!options.containsKey(key) || options.get(key).isBlank())
            throw new IllegalArgumentException("Required argument: --" + key);
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }
}
