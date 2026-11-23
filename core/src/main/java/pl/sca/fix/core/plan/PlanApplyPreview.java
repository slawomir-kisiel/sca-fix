package pl.sca.fix.core.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitStatus;

/** Dry-run of apply using the same validation and editor as production apply. */
public final class PlanApplyPreview {
    private PlanApplyPreview() { }

    public static void main(String[] args) {
        try {
            Map<String, String> options = options(args);
            PlanDocument plan = new PlanDocumentJsonCodec().read(Files.readAllBytes(Path.of(required(options, "plan"))));
            Path repository = Path.of(required(options, "repository")).toAbsolutePath().normalize();
            PlanApplyResult result = new PlanApplyService(new NioFileSystemAdapter(),
                    new PreviewGitPort(plan.repository().commitSha())).apply(new PlanApplyRequest(plan, repository,
                            options.containsKey("ignore-local-changes"), options.containsKey("existing-execution"), true));
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            if (options.containsKey("output")) {
                Path output = Path.of(options.get("output")).toAbsolutePath().normalize();
                if (output.getParent() != null) Files.createDirectories(output.getParent());
                Files.write(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result.summary()));
            }
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.summary()));
        } catch (Exception exception) {
            System.err.println("Plan apply preview failed: " + exception.getMessage());
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
            if (key.equals("ignore-local-changes") || key.equals("existing-execution") || key.equals("verbose")) {
                result.put(key, "true");
                continue;
            }
            if (++i >= args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Missing value for --" + key);
            result.put(key, args[i]);
        }
        required(result, "plan");
        required(result, "repository");
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

    private record PreviewGitPort(String commitSha) implements GitPort {
        @Override public GitStatus status(Path repository) { return new GitStatus(true, true, commitSha, List.of()); }
        @Override public List<Path> trackedFiles(Path repository) { return List.of(); }
        @Override public void add(Path repository, List<Path> paths) { }
        @Override public pl.sca.fix.core.port.GitBranch createBranch(Path repository, String branchName) {
            return new pl.sca.fix.core.port.GitBranch(branchName, commitSha);
        }
        @Override public pl.sca.fix.core.port.GitBranch checkoutBranch(Path repository, String branchName) {
            return new pl.sca.fix.core.port.GitBranch(branchName, commitSha);
        }
        @Override public void deleteBranch(Path repository, String branchName) { }
        @Override public String commit(Path repository, String message) { return commitSha; }
        @Override public void push(Path repository, String branchName) { }
    }
}
