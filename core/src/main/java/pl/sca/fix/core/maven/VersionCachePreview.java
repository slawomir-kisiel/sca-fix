package pl.sca.fix.core.maven;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.port.VersionRepositoryClientPort;

/** Command-line preview of Maven version lookup and persistent cache behavior. */
public final class VersionCachePreview {
    private VersionCachePreview() { }

    public static void main(String[] args) {
        try {
            System.out.print(run(args));
        } catch (Exception exception) {
            System.err.println("Version cache preview failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) {
        ParsedOptions options = options(args);
        VersionRepositoryClientPort client = new MavenMetadataClient();
        VersionCacheService service = new VersionCacheService(new NioFileSystemAdapter(), client,
                java.time.Instant::now, Path.of(options.values().getOrDefault("cache", "target/scafix/poms")),
                options.blockDuration());
        VersionCacheResult result = service.findVersions(options.values().get("group"),
                options.values().get("artifact"), options.repositories(),
                VersionInfoTtl.parse(options.values().get("ttl")), options.timeout());
        StringBuilder output = new StringBuilder();
        output.append("source: ").append(result.fromCache() ? "CACHE" : "MAVEN").append('\n');
        output.append("coordinate: ").append(options.values().get("group")).append(':')
                .append(options.values().get("artifact")).append('\n');
        output.append("versions: ").append(result.versions().size()).append('\n');
        result.versions().forEach(version -> output.append("  ").append(version.version())
                .append(" [").append(version.kind()).append("] from ")
                .append(String.join(",", version.repositoryUrls())).append('\n'));
        if (!result.warnings().isEmpty()) {
            output.append("warnings:\n");
            result.warnings().forEach(warning -> output.append("  ").append(warning).append('\n'));
        }
        return output.toString();
    }

    private static ParsedOptions options(String[] args) {
        Map<String, String> values = new HashMap<>();
        List<MavenRepositorySpec> repositories = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) throw new IllegalArgumentException("Unknown argument: " + argument);
            String key = argument.substring(2);
            if (key.equals("verbose")) { values.put(key, "true"); continue; }
            if (index + 1 >= args.length || args[index + 1].startsWith("--"))
                throw new IllegalArgumentException("Missing value for --" + key);
            String value = args[++index];
            if (key.equals("repo")) repositories.add(new MavenRepositorySpec(value, value, true, true));
            else values.put(key, value);
        }
        required(values, "group");
        required(values, "artifact");
        if (repositories.isEmpty()) throw new IllegalArgumentException("Required argument: --repo <url>");
        return new ParsedOptions(values, repositories);
    }

    private static void required(Map<String, String> values, String key) {
        if (!values.containsKey(key) || values.get(key).isBlank())
            throw new IllegalArgumentException("Required argument: --" + key);
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }

    private record ParsedOptions(Map<String, String> values, List<MavenRepositorySpec> repositories) {
        private Duration timeout() { return duration(values.getOrDefault("timeout", "30s")); }
        private Duration blockDuration() { return duration(values.getOrDefault("block", "10m")); }
        private static Duration duration(String value) {
            return VersionInfoTtl.parse("S" + value).snapshot();
        }
    }
}
