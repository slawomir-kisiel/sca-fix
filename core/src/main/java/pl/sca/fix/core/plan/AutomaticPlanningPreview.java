package pl.sca.fix.core.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.maven.EffectivePom;
import pl.sca.fix.core.maven.VersionCacheDocument;
import pl.sca.fix.core.report.CanonicalReportJsonCodec;
import pl.sca.fix.core.report.CanonicalScanReport;
import pl.sca.fix.core.report.CorrelationJsonCodec;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.UpdateLevel;

/** Manual preview of automatic plan preparation from persisted core artifacts. */
public final class AutomaticPlanningPreview {
    private AutomaticPlanningPreview() { }

    public static void main(String[] args) {
        try { System.out.print(run(args)); }
        catch (Exception exception) {
            System.err.println("Automatic planning preview failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) throws Exception {
        Map<String, String> options = options(args);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path repository = Path.of(options.get("repository")).toAbsolutePath().normalize();
        CorrelationResult correlation = new CorrelationJsonCodec().read(Files.readAllBytes(Path.of(options.get("correlation"))));
        CanonicalScanReport report = options.containsKey("report")
                ? new CanonicalReportJsonCodec().read(Files.readAllBytes(Path.of(options.get("report")))) : null;
        List<PlanningProject> projects = mapper.readValue(Files.readAllBytes(Path.of(options.get("effective-poms"))),
                new TypeReference<>() { });
        Map<String, List<VersionCacheDocument.VersionEntry>> versions = loadVersions(mapper,
                Path.of(options.get("versions")));
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(report, correlation, projects, versions,
                repository, options.get("commit-sha"), UpdateLevel.parse(options.get("update-level")),
                AmbiguousStrategy.parse(options.get("ambiguous-strategy")), hasFlag(args, "override-review"));
        Path output = Path.of(options.getOrDefault("output", "plan.json")).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, new PlanDocumentJsonCodec().write(result.plan()));
        StringBuilder text = new StringBuilder();
        text.append("findings: ").append(result.summary().findings()).append('\n');
        text.append("libraryGroups: ").append(result.summary().libraryGroups()).append('\n');
        text.append("occurrences: ").append(result.summary().occurrences()).append('\n');
        text.append("plannedItems: ").append(result.summary().plannedItems()).append('\n');
        text.append("skippedItems: ").append(result.summary().skippedItems()).append('\n');
        text.append("reasons: ").append(result.summary().reasons()).append('\n');
        text.append("output: ").append(output).append('\n');
        return text.toString();
    }

    private static Map<String, List<VersionCacheDocument.VersionEntry>> loadVersions(ObjectMapper mapper, Path path) throws Exception {
        Map<String, List<VersionCacheDocument.VersionEntry>> result = new LinkedHashMap<>();
        List<Path> files = Files.isDirectory(path) ? Files.walk(path).filter(item -> item.getFileName().toString().equals("versions.json")).sorted().toList() : List.of(path);
        for (Path file : files) {
            VersionCacheDocument document = mapper.readValue(Files.readAllBytes(file), VersionCacheDocument.class);
            Map<String, VersionCacheDocument.VersionEntry> entries = new LinkedHashMap<>();
            document.repositories().forEach(repository -> repository.versions().forEach(version -> entries.putIfAbsent(version,
                    new VersionCacheDocument.VersionEntry(version, List.of(repository.url()),
                            pl.sca.fix.core.maven.MavenVersionComparator.isSnapshot(version) ? "SNAPSHOT" : "RELEASE"))));
            result.put(document.groupId() + ":" + document.artifactId(), entries.values().stream().toList());
        }
        return result;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) throw new IllegalArgumentException("Unknown argument: " + argument);
            String key = argument.substring(2);
            if (key.equals("override-review") || key.equals("verbose")) { result.put(key, "true"); continue; }
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) throw new IllegalArgumentException("Missing value for --" + key);
            result.put(key, args[++index]);
        }
        for (String key : List.of("correlation", "effective-poms", "versions", "repository", "commit-sha"))
            if (!result.containsKey(key) || result.get(key).isBlank()) throw new IllegalArgumentException("Required argument: --" + key);
        if (!result.containsKey("report") && !result.containsKey("override-review"))
            throw new IllegalArgumentException("Required argument: --report or --override-review");
        return result;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }
}
