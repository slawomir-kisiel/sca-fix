package pl.sca.fix.core.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.maven.NioFileSystemAdapter;

/** Command-line preview for one POM change. The input POM is never modified. */
public final class PomEditorPreview {
    private PomEditorPreview() { }

    public static void main(String[] args) {
        try {
            Map<String, String> options = options(args);
            PomEditRequest request = new PomEditRequest(
                    Path.of(required(options, "pom")), required(options, "strategy"),
                    required(options, "group"), required(options, "artifact"),
                    options.get("current-version"), options.get("target-version"), options.get("property"),
                    options.get("scope"), options.get("type"), options.get("classifier"),
                    options.getOrDefault("operation", "ADD_OR_UPDATE"), Map.of(), options.get("expected-sha256"));
            PomChange change = new PomEditor(new NioFileSystemAdapter()).prepare(request);
            if (options.containsKey("output")) {
                Path output = Path.of(options.get("output")).toAbsolutePath().normalize();
                if (output.getParent() != null) Files.createDirectories(output.getParent());
                ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
                Files.write(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(change));
            }
            System.out.println("pom: " + change.pomPath());
            System.out.println("strategy: " + change.strategy());
            System.out.println("changed: " + change.changed());
            System.out.println("indentation: " + printable(change.indentation()));
            System.out.println("originalSha256: " + change.originalSha256());
            System.out.println("changedSha256: " + change.changedSha256());
            if (options.containsKey("output")) System.out.println("output: " + Path.of(options.get("output")).toAbsolutePath().normalize());
            if (!change.diff().isBlank()) System.out.print(change.diff());
        } catch (Exception exception) {
            System.err.println("POM editor preview failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (!argument.startsWith("--")) throw new IllegalArgumentException("Unknown argument: " + argument);
            String key = argument.substring(2);
            if (key.equals("verbose")) { result.put(key, "true"); continue; }
            if (++i >= args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Missing value for --" + key);
            result.put(key, args[i]);
        }
        for (String key : List.of("pom", "strategy", "group", "artifact")) required(result, key);
        return result;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required argument: --" + key);
        return value;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }

    private static String printable(String value) { return value.replace("\t", "\\t").replace(" ", "·"); }
}
