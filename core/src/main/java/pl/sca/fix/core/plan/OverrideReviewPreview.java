package pl.sca.fix.core.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.maven.MavenProcessAdapter;
import pl.sca.fix.core.maven.MavenToolConfiguration;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.maven.SystemProcessRunner;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.port.MavenPort;

/** Standalone preview for OverrideReview, including the no-report workflow. */
public final class OverrideReviewPreview {
    private OverrideReviewPreview() { }

    public static void main(String[] args) {
        try { System.out.print(run(args)); }
        catch (Exception exception) {
            System.err.println("Override review preview failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) throws Exception {
        Map<String, String> options = options(args);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path repository = Path.of(options.get("repository")).toAbsolutePath().normalize();
        List<PlanningProject> projects = mapper.readValue(
                Files.readAllBytes(Path.of(options.get("effective-poms"))), new TypeReference<>() { });
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        MavenPort maven = new MavenProcessAdapter(new SystemProcessRunner(), new NioFileSystemAdapter(),
                MavenToolConfiguration.defaults(windows));
        OverrideReviewResult result = new OverrideReviewService(new NioFileSystemAdapter(), maven,
                Path.of(options.getOrDefault("probe-output", "target/scafix/override-review")))
                .review(projects, repository, options.get("commit-sha"),
                        UpdateLevel.parse(options.getOrDefault("update-level", "MINOR")));
        Path output = Path.of(options.getOrDefault("output", "plan.json")).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, new PlanDocumentJsonCodec().write(result.plan()));
        long redundant = result.entries().stream().filter(item -> item.status() == OverrideReviewStatus.REDUNDANT).count();
        long keep = result.entries().stream().filter(item -> item.status() == OverrideReviewStatus.KEEP).count();
        long review = result.entries().stream().filter(item -> item.status() == OverrideReviewStatus.REVIEW_REQUIRED).count();
        long inconclusive = result.entries().stream().filter(item -> item.status() == OverrideReviewStatus.INCONCLUSIVE).count();
        return "overrides: " + result.entries().size() + '\n'
                + "redundant: " + redundant + '\n'
                + "keep: " + keep + '\n'
                + "reviewRequired: " + review + '\n'
                + "inconclusive: " + inconclusive + '\n'
                + "planItems: " + result.plan().items().size() + '\n'
                + "output: " + output + '\n';
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
        for (String key : List.of("effective-poms", "repository", "commit-sha"))
            if (!result.containsKey(key) || result.get(key).isBlank())
                throw new IllegalArgumentException("Required argument: --" + key);
        return result;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }
}
