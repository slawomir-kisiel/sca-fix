package pl.sca.fix.core.report;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.port.ReportDocument;

/** Manual preview of report detection, adaptation and canonical JSON output. */
public final class CanonicalReportPreview {
    private CanonicalReportPreview() { }

    public static void main(String[] args) {
        try { System.out.print(run(args)); }
        catch (Exception exception) {
            System.err.println("Canonical report preview failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) {
        Map<String, String> options = options(args);
        Path input = Path.of(options.get("input")).toAbsolutePath().normalize();
        NioFileSystemAdapter files = new NioFileSystemAdapter();
        if (!files.exists(input) || !files.isDirectory(input) && !java.nio.file.Files.isRegularFile(input))
            throw new IllegalArgumentException("Report file does not exist: " + input);
        ReportImportResult result = new ScanReportImporter().importReport(new ReportDocument(
                files.readBytes(input), input, options.get("format")));
        CanonicalScanReport report = result.report();
        StringBuilder output = new StringBuilder();
        output.append("format: ").append(report.metadata() == null ? "UNKNOWN" : report.metadata().source()).append('\n');
        output.append("status: ").append(report.status()).append('\n');
        output.append("vulnerableLibraries: ").append(report.vulnerableLibraries().size()).append('\n');
        output.append("cves: ").append(report.cves().size()).append('\n');
        for (VulnerableLibrary library : report.vulnerableLibraries()) {
            output.append("  ").append(library.groupId()).append(':').append(library.artifactId())
                    .append(':').append(library.version()).append(" severity=").append(library.severity())
                    .append(" cvss=").append(library.cvss()).append(" cve=").append(library.cve()).append('\n');
        }
        if (!report.warnings().isEmpty()) output.append("warnings: ").append(report.warnings()).append('\n');
        if (!report.errors().isEmpty()) output.append("errors: ").append(report.errors()).append('\n');
        String outputPath = options.get("output");
        if (outputPath != null) {
            Path target = Path.of(outputPath).toAbsolutePath().normalize();
            files.writeAtomically(target, new CanonicalReportJsonCodec().write(report));
            output.append("output: ").append(target).append('\n');
        }
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
        if (!result.containsKey("input")) throw new IllegalArgumentException("Required argument: --input");
        return result;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }
}
