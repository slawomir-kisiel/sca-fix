package pl.sca.fix.core.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import pl.sca.fix.core.port.MavenDependencyTreeRequest;
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.core.port.PortException;

/** Command-line preview of the normalized dependency tree. */
public final class DependencyTreePreview {
    private DependencyTreePreview() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception exception) {
            System.err.println("Dependency tree preview failed: " + exception.getMessage());
            if (hasFlag(args, "--verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) {
        Map<String, String> options = options(args);
        Path pom = requiredPath(options, "pom");
        if (!Files.isRegularFile(pom)) throw new PortException(PortException.Kind.NOT_FOUND,
                "POM file does not exist: " + pom);
        Path output = Path.of(options.getOrDefault("output", pom.resolveSibling("dependency-tree.tree.txt").toString()));
        Path nativeJson = output.resolveSibling(output.getFileName() + ".maven.json");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        MavenToolConfiguration defaults = MavenToolConfiguration.defaults(windows);
        String version = options.getOrDefault("plugin-version", defaults.dependencyPluginVersion());
        MavenPort maven = new MavenProcessAdapter(new SystemProcessRunner(), new NioFileSystemAdapter(),
                new MavenToolConfiguration(defaults.executable(), windows, defaults.helpPluginVersion(),
                        version, defaults.timeout()));
        maven.generateDependencyTree(new MavenDependencyTreeRequest(pom, nativeJson, version, false));
        DependencyTree tree = new JsonDependencyTreeParser().parse(nativeJson);
        new DependencyTreeFileWriter(new NioFileSystemAdapter()).write(output, tree);
        String rendered = new String(new NioFileSystemAdapter().readBytes(output), java.nio.charset.StandardCharsets.UTF_8);
        System.out.print(rendered);
        return rendered;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) throw new IllegalArgumentException("Unknown argument: " + argument);
            String key = argument.substring(2);
            if ("verbose".equals(key)) { result.put(key, "true"); continue; }
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
            result.put(key, args[++index]);
        }
        if (!result.containsKey("pom")) throw new IllegalArgumentException("Required argument: --pom");
        return result;
    }

    private static Path requiredPath(Map<String, String> options, String key) {
        return Path.of(options.get(key)).toAbsolutePath().normalize();
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (flag.equals(arg)) return true;
        return false;
    }
}
