package pl.sca.fix.core.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.report.UpdateLevel;

/** Manual preview of version recommendation from a persisted versions.json. */
public final class VersionRecommendationPreview {
    private VersionRecommendationPreview() { }

    public static void main(String[] args) {
        try { System.out.print(run(args)); }
        catch (Exception exception) {
            System.err.println("Version recommendation preview failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) throws Exception {
        Map<String, String> options = options(args);
        String versionsFile = options.containsKey("versions-file") ? options.get("versions-file") : options.get("cache");
        Path versionsPath = Path.of(versionsFile)
                .toAbsolutePath().normalize();
        VersionCacheDocument document = new ObjectMapper().findAndRegisterModules()
                .readValue(java.nio.file.Files.readAllBytes(versionsPath), VersionCacheDocument.class);
        Map<String, VersionCacheDocument.VersionEntry> entries = new LinkedHashMap<>();
        for (VersionCacheDocument.RepositoryState repository : document.repositories()) {
            for (String version : repository.versions()) {
                VersionCacheDocument.VersionEntry previous = entries.get(version);
                List<String> repositories = new ArrayList<>(previous == null ? List.of() : previous.repositoryUrls());
                if (!repositories.contains(repository.url())) repositories.add(repository.url());
                entries.put(version, new VersionCacheDocument.VersionEntry(version, repositories,
                        MavenVersionComparator.isSnapshot(version) ? "SNAPSHOT" : "RELEASE"));
            }
        }
        VersionRecommendation recommendation = new VersionRecommendationService().recommend(
                options.get("group"), options.get("artifact"), options.get("current-version"),
                entries.values().stream().toList(), UpdateLevel.parse(options.get("update-level")));
        Path output = Path.of(options.getOrDefault("output", "version-recommendation.json"))
                .toAbsolutePath().normalize();
        new NioFileSystemAdapter().writeAtomically(output, new VersionRecommendationJsonCodec().write(recommendation));
        StringBuilder result = new StringBuilder();
        result.append("artifact: ").append(recommendation.groupId()).append(':').append(recommendation.artifactId()).append('\n');
        result.append("currentVersion: ").append(recommendation.currentVersion()).append('\n');
        result.append("updateLevel: ").append(recommendation.updateLevel()).append('\n');
        result.append("recommendedVersion: ").append(recommendation.recommendedVersion()).append('\n');
        result.append("eligibleVersions: ").append(recommendation.eligibleVersions().size()).append('\n');
        result.append("rejections: ").append(recommendation.rejections()).append('\n');
        result.append("output: ").append(output).append('\n');
        return result.toString();
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
        for (String key : List.of("group", "artifact", "current-version"))
            if (!result.containsKey(key) || result.get(key).isBlank()) throw new IllegalArgumentException("Required argument: --" + key);
        if ((!result.containsKey("versions-file") || result.get("versions-file").isBlank())
                && (!result.containsKey("cache") || result.get("cache").isBlank()))
            throw new IllegalArgumentException("Required argument: --versions-file or --cache");
        return result;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }
}
