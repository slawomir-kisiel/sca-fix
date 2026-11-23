package pl.sca.fix.core.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import pl.sca.fix.core.port.MavenPort;

/** Command-line preview of the effective-POM model, cache and runtime index. */
public final class EffectivePomPreview {
    private EffectivePomPreview() { }

    public static void main(String[] args) {
        try {
            System.out.print(run(args));
        } catch (Exception exception) {
            System.err.println("Effective POM preview failed: " + exception.getMessage());
            if (hasFlag(args, "--verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) {
        Map<String, String> options = options(args);
        Path pom = Path.of(options.get("pom")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(pom)) throw new IllegalArgumentException("POM file does not exist: " + pom);
        Path repository = Path.of(options.getOrDefault("repository", pom.getParent().toString()))
                .toAbsolutePath().normalize();
        Path cache = Path.of(options.getOrDefault("cache", "target/scafix/poms"))
                .toAbsolutePath().normalize();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        MavenToolConfiguration defaults = MavenToolConfiguration.defaults(windows);
        MavenPort maven = new MavenProcessAdapter(new SystemProcessRunner(), new NioFileSystemAdapter(), defaults);
        EffectivePomCache.Loaded loaded = new EffectivePomCache(new NioFileSystemAdapter(), maven)
                .load(new EffectivePomCacheRequest(repository, pom, cache, hasFlag(args, "--refresh")));
        EffectivePom model = loaded.model();
        StringBuilder output = new StringBuilder();
        output.append("source: ").append(loaded.fromCache() ? "CACHE" : "MAVEN").append('\n');
        output.append("coordinate: ").append(model.groupId()).append(':').append(model.artifactId())
                .append(':').append(model.version()).append('\n');
        output.append("parent: ").append(format(model.parent())).append('\n');
        output.append("pomChain: ").append(model.pomChain().size()).append('\n');
        for (EffectivePom.PomSource source : model.pomChain()) {
            output.append("  ").append(source.groupId()).append(':').append(source.artifactId())
                    .append(':').append(source.version()).append(" local=").append(source.local())
                    .append(" path=").append(source.pomPath()).append('\n');
        }
        output.append("dependencies: ").append(model.dependencies().size()).append('\n');
        model.dependencies().forEach(dependency -> output.append("  ").append(format(dependency)).append('\n'));
        output.append("dependencyManagement: ").append(model.dependencyManagement().size()).append('\n');
        model.dependencyManagement().forEach(dependency -> output.append("  ").append(format(dependency)).append('\n'));
        output.append("index: ").append(model.dependencyIndex().size()).append(" keys\n");
        model.dependencyIndex().forEach((key, values) -> output.append("  ").append(key)
                .append(" -> ").append(values.size()).append(" declaration(s)\n"));
        String find = options.get("find");
        if (find != null) {
            output.append("find ").append(find).append(':').append('\n');
            model.dependencyIndex().getOrDefault(find, java.util.List.of()).forEach(value ->
                    output.append("  ").append(value.section()).append(" ")
                            .append(format(value.dependency())).append('\n'));
        }
        return output.toString();
    }

    private static String format(EffectivePom.Dependency value) {
        if (value == null) return "none";
        StringBuilder result = new StringBuilder(value.groupId()).append(':').append(value.artifactId())
                .append(':').append(value.version());
        if (value.scope() != null) result.append(" scope=").append(value.scope());
        if (value.type() != null) result.append(" type=").append(value.type());
        if (value.classifier() != null) result.append(" classifier=").append(value.classifier());
        if (value.versionLocation() != null) {
            result.append(" location=").append(value.versionLocation().artifact());
            if (value.versionLocation().line() != null) result.append(':').append(value.versionLocation().line());
        }
        return result.toString();
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) throw new IllegalArgumentException("Unknown argument: " + argument);
            String key = argument.substring(2);
            if (key.equals("refresh") || key.equals("verbose")) { result.put(key, "true"); continue; }
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
            result.put(key, args[++index]);
        }
        if (!result.containsKey("pom")) throw new IllegalArgumentException("Required argument: --pom");
        return result;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }
}
