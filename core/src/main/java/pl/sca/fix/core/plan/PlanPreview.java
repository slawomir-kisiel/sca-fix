package pl.sca.fix.core.plan;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import pl.sca.fix.core.maven.NioFileSystemAdapter;

/** Manual preview and validation of a portable plan.json. */
public final class PlanPreview {
    private PlanPreview() { }

    public static void main(String[] args) {
        try { System.out.print(run(args)); }
        catch (Exception exception) {
            System.err.println("Plan preview failed: " + exception.getMessage());
            if (hasFlag(args, "verbose")) exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String run(String[] args) {
        Map<String, String> options = options(args);
        Path planPath = Path.of(options.get("plan")).toAbsolutePath().normalize();
        PlanDocument plan = new PlanDocumentJsonCodec().read(new NioFileSystemAdapter().readBytes(planPath));
        StringBuilder output = new StringBuilder();
        output.append("planId: ").append(plan.planId()).append('\n');
        output.append("schemaVersion: ").append(plan.schemaVersion()).append('\n');
        output.append("repository: ").append(plan.repository().path()).append('\n');
        output.append("commitSha: ").append(plan.repository().commitSha()).append('\n');
        output.append("updateLevel: ").append(plan.updateLevel()).append('\n');
        output.append("items: ").append(plan.items().size()).append('\n');
        for (PlanItem item : plan.items()) {
            output.append("  ").append(item.itemId()).append(' ')
                    .append(item.artifact().groupId()).append(':').append(item.artifact().artifactId())
                    .append(' ').append(item.currentVersion()).append(" -> ")
                    .append(item.targetVersion()).append(" strategy=").append(item.strategy()).append('\n');
        }
        output.append("warnings: ").append(plan.warnings().size()).append('\n');
        output.append("errors: ").append(plan.errors().size()).append('\n');
        output.append("status: VALID\n");
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
        if (!result.containsKey("plan") || result.get("plan").isBlank())
            throw new IllegalArgumentException("Required argument: --plan");
        return result;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (("--" + flag).equals(arg)) return true;
        return false;
    }
}
