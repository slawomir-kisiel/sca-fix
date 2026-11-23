package pl.sca.fix.core.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pl.sca.fix.core.maven.EffectivePom;
import pl.sca.fix.core.maven.EffectivePomJsonCodec;
import pl.sca.fix.core.maven.NioFileSystemAdapter;

/** Generates the PlanningProject manifest consumed by AutomaticPlanningPreview. */
public final class PlanningProjectManifestGenerator {
    private PlanningProjectManifestGenerator() { }

    public static void main(String[] args) {
        try { System.out.print(run(args)); }
        catch (Exception exception) {
            System.err.println("Planning project manifest generation failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) throws Exception {
        Options options = Options.parse(args);
        Path repository = options.repository().toAbsolutePath().normalize();
        EffectivePomJsonCodec codec = new EffectivePomJsonCodec(new NioFileSystemAdapter());
        List<PlanningProject> result = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try (var files = Files.walk(options.cache())) {
            files.filter(path -> path.getFileName().toString().equals("effective-pom.json"))
                    .sorted().forEach(path -> add(path, repository, codec, result, warnings));
        }
        result.sort(Comparator.comparing(PlanningProject::projectId));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
        Files.createDirectories(options.output().toAbsolutePath().normalize().getParent());
        Files.write(options.output(), mapper.writeValueAsBytes(result));
        StringBuilder output = new StringBuilder();
        output.append("projects: ").append(result.size()).append('\n');
        output.append("warnings: ").append(warnings.size()).append('\n');
        warnings.forEach(warning -> output.append("  ").append(warning).append('\n'));
        output.append("output: ").append(options.output().toAbsolutePath().normalize()).append('\n');
        return output.toString();
    }

    private static void add(Path json, Path repository, EffectivePomJsonCodec codec,
                            List<PlanningProject> result, List<String> warnings) {
        try {
            EffectivePom pom = codec.read(json);
            EffectivePom.PomSource source = pom.pomChain().stream().filter(EffectivePom.PomSource::local).findFirst().orElse(null);
            if (source == null || source.pomPath() == null) {
                warnings.add("No local POM source in " + json);
                return;
            }
            Path pomPath = resolvePomPath(repository, source.pomPath());
            boolean editable = pomPath.startsWith(repository) && Files.isRegularFile(pomPath);
            result.add(new PlanningProject(source.groupId() + ":" + source.artifactId() + ":" + source.version(),
                    pomPath, pom, editable));
        } catch (RuntimeException exception) {
            warnings.add("Unable to read " + json + ": " + exception.getMessage());
        }
    }

    private static Path resolvePomPath(Path repository, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("POM source path is blank");
        if (value.startsWith("file:")) return Path.of(URI.create(value)).toAbsolutePath().normalize();
        Path candidate = Path.of(value);
        return (candidate.isAbsolute() ? candidate : repository.resolve(candidate)).toAbsolutePath().normalize();
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }

    private record Options(Path cache, Path repository, Path output) {
        private static Options parse(String[] args) {
            java.util.Map<String, String> values = new java.util.HashMap<>();
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if (!argument.startsWith("--")) throw new IllegalArgumentException("Unknown argument: " + argument);
                String key = argument.substring(2);
                if (key.equals("verbose")) continue;
                if (index + 1 >= args.length || args[index + 1].startsWith("--"))
                    throw new IllegalArgumentException("Missing value for --" + key);
                values.put(key, args[++index]);
            }
            Path cache = required(values, "cache");
            Path repository = required(values, "repository");
            Path output = required(values, "output");
            if (!Files.isDirectory(cache)) throw new IllegalArgumentException("Cache directory does not exist: " + cache);
            return new Options(cache, repository, output);
        }

        private static Path required(java.util.Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Required argument: --" + key);
            return Path.of(value);
        }
    }
}
