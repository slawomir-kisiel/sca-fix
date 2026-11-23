package pl.sca.fix.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.plan.PlanSkipRule;

/** Parses and validates CLI configuration without starting any external process. */
public final class CliConfigurationParser {
    private static final Logger LOG = LoggerFactory.getLogger(CliConfigurationParser.class);
    private static final Set<String> KNOWN = Set.of("schemaVersion", "repository", "projects", "report-source", "report-input",
            "report-output", "plan", "update-level", "ambiguous-strategy", "version-info-ttl",
            "ignore-local-changes", "commit", "push", "override-review", "workspace-root", "poms-root",
            "reportAvailable", "apply", "disable-strategy", "skip", "cve-override-update-level", "verbose", "proxy-host", "proxy-port", "proxy-username", "proxy-password");
    private static final Map<String, String> CONFIG_ALIASES = Map.ofEntries(
            Map.entry("updateLevel", "update-level"), Map.entry("ambiguousStrategy", "ambiguous-strategy"),
            Map.entry("versionInfoTtl", "version-info-ttl"), Map.entry("ignoreLocalChanges", "ignore-local-changes"),
            Map.entry("overrideReview", "override-review"), Map.entry("workspaceRoot", "workspace-root"),
            Map.entry("pomsRoot", "poms-root"), Map.entry("reportAvailable", "reportAvailable"),
            Map.entry("proxyHost", "proxy-host"), Map.entry("proxyPort", "proxy-port"),
            Map.entry("proxyUsername", "proxy-username"), Map.entry("proxyPassword", "proxy-password"),
            Map.entry("disableStrategy", "disable-strategy"), Map.entry("disabledStrategies", "disable-strategy"),
            Map.entry("skipLibraries", "skip"), Map.entry("cveOverrideUpdateLevel", "cve-override-update-level"));
    private static final Set<String> BOOLEAN = Set.of("ignore-local-changes", "commit", "override-review", "apply");
    private final ObjectMapper mapper = new ObjectMapper();

    public CliConfiguration parse(String... arguments) {
        List<String> args = List.of(arguments == null ? new String[0] : arguments);
        if (args.isEmpty()) error("MISSING_COMMAND", null, "A command is required: scan, override-review or apply");
        CliCommand command = command(args.get(0));
        Map<String, Object> values = defaults();
        int index = 1;
        ScanMode mode = ScanMode.NEW;
        String workspaceId = null;
        String configFile = null;
        boolean help = false;
        boolean transitionModeSeen = false;
        boolean workspaceOptionSeen = false;
        Map<String, Object> cliValues = new LinkedHashMap<>();
        while (index < args.size()) {
            String token = args.get(index++);
            if (token.equals("--help") || token.equals("-h")) { help = true; continue; }
            if (token.equals("--verbose")) { cliValues.put("verbose", true); continue; }
            if (token.equals("--continue") || token.equals("--restart")) {
                if (transitionModeSeen) error("MODE_CONFLICT", token, "--continue and --restart are mutually exclusive");
                if (command == CliCommand.APPLY) error("MODE_NOT_ALLOWED", token, "Apply does not accept scan mode options");
                if (index >= args.size()) error("MISSING_VALUE", token, "Workspace ID is required");
                mode = token.equals("--continue") ? ScanMode.CONTINUE : ScanMode.RESTART;
                transitionModeSeen = true;
                workspaceId = args.get(index++);
                continue;
            }
            if (!token.startsWith("--")) error("UNKNOWN_ARGUMENT", token, "Unknown argument: " + token);
            String name = token.substring(2);
            String inline = null;
            int equals = name.indexOf('=');
            if (equals >= 0) { inline = name.substring(equals + 1); name = name.substring(0, equals); }
            if (name.equals("config")) {
                configFile = value(token, inline, args, index); if (inline == null) index++;
                continue;
            }
            if (name.equals("workspace")) {
                workspaceOptionSeen = true;
                workspaceId = value(token, inline, args, index); if (inline == null) index++;
                continue;
            }
            String key = name;
            if (key.equals("no-ignore-local-changes")) { cliValues.put("ignore-local-changes", false); continue; }
            if (!KNOWN.contains(key)) error("UNKNOWN_ARGUMENT", token, "Unknown argument: " + token);
            if (BOOLEAN.contains(key)) {
                cliValues.put(key, inline == null ? true : booleanValue(key, inline));
            } else {
                String value = value(token, inline, args, index); if (inline == null) index++;
                cliValues.put(key, value);
            }
        }
        if (workspaceId != null && (mode == ScanMode.CONTINUE || command == CliCommand.APPLY)) {
            String workspaceRoot = String.valueOf(cliValues.getOrDefault("workspace-root", values.get("workspace-root")));
            mergeWorkspace(values, workspaceId, workspaceRoot);
        }
        if (configFile != null) mergeFile(values, Path.of(configFile));
        values.putAll(cliValues);
        values.put("cve-override-update-level", resolvedCveOverrideUpdateLevel(values, cliValues));
        if (command == CliCommand.SCAN && mode == ScanMode.CONTINUE
                && string(values, "report-source") == null && bool(values, "reportAvailable")) {
            values.put("report-source", "file");
            values.put("report-input", Path.of(string(values, "workspace-root"), workspaceId, "report.json").toString());
        }
        if (help) return null;
        validate(command, mode, workspaceId, workspaceOptionSeen, values);
        return build(command, mode, workspaceId, configFile, values);
    }

    private CliConfiguration build(CliCommand command, ScanMode mode, String workspaceId,
                                   String configFile, Map<String, Object> v) {
        return new CliConfiguration(command, mode, workspaceId, string(v, "repository"), list(v.get("projects")),
                string(v, "report-source"), string(v, "report-input"), string(v, "report-output"),
                string(v, "plan"), enumValue(UpdateLevel.class, string(v, "update-level"), "update-level"),
                string(v, "ambiguous-strategy"), string(v, "version-info-ttl"), bool(v, "ignore-local-changes"),
                bool(v, "commit"), pushBranch(v), bool(v, "override-review"), configFile,
                Path.of(string(v, "workspace-root")), Path.of(string(v, "poms-root")), bool(v, "verbose"),
                string(v, "proxy-host"), integer(v, "proxy-port"), string(v, "proxy-username"),
                string(v, "proxy-password"), bool(v, "apply"), listOrEmpty(v.get("disable-strategy")), listOrEmpty(v.get("skip")),
                enumValue(UpdateLevel.class, string(v, "cve-override-update-level"), "cve-override-update-level"));
    }

    private void validate(CliCommand command, ScanMode mode, String workspaceId, boolean workspaceOptionSeen, Map<String, Object> v) {
        proxy(v);
        try { PlanSkipRule.parseAll(listOrEmpty(v.get("skip"))); }
        catch (IllegalArgumentException exception) { error("INVALID_VALUE", "skip", exception.getMessage()); }
        if (command != CliCommand.SCAN && bool(v, "apply"))
            error("APPLY_NOT_ALLOWED", "apply", "--apply is available only for scan");
        if (!listOrEmpty(v.get("disable-strategy")).isEmpty()
                && command != CliCommand.APPLY && !(command == CliCommand.SCAN && bool(v, "apply")))
            error("DISABLE_STRATEGY_NOT_ALLOWED", "disable-strategy", "--disable-strategy is available only for apply or scan --apply");
        if (command == CliCommand.SCAN && bool(v, "apply") && string(v, "plan") != null)
            error("PLAN_NOT_ALLOWED", "plan", "scan --apply uses the plan generated by the pipeline");
        if (command == CliCommand.SCAN && workspaceOptionSeen) {
            error("WORKSPACE_MODE_REQUIRED", "workspace", "Scan workspace requires --continue or --restart");
        }
        if ((mode != ScanMode.NEW) && (workspaceId == null || workspaceId.isBlank())) error("WORKSPACE_REQUIRED", "workspace", "Workspace ID is required");
        if (command == CliCommand.APPLY) {
            boolean hasPlan = v.containsKey("plan") && string(v, "plan") != null;
            boolean hasWorkspace = workspaceId != null;
            if (hasPlan == hasWorkspace) error("PLAN_SOURCE_CONFLICT", "plan", "Apply requires exactly one of --plan or --workspace");
            if (hasPush(v) && !bool(v, "commit")) error("PUSH_REQUIRES_COMMIT", "push", "Push requires commit");
            if (hasPush(v) && bool(v, "ignore-local-changes")) error("PUSH_WITH_IGNORED_CHANGES", "push", "Push is blocked with ignore-local-changes");
            return;
        }
        if (command == CliCommand.OVERRIDE_REVIEW) {
            if (v.get("report-input") != null || v.get("report-source") != null) error("REPORT_NOT_ALLOWED", "report-input", "OverrideReview does not accept a report");
            return;
        }
        String source = string(v, "report-source");
        if (source == null) error("REPORT_REQUIRED", "report-source", "Scan requires a report source");
        if (source.equals("file") && string(v, "report-input") == null) error("REPORT_INPUT_REQUIRED", "report-input", "File report source requires report input");
        if (!Set.of("file", "generate").contains(source)) error("INVALID_REPORT_SOURCE", "report-source", "Unsupported report source: " + source);
        if (hasPush(v) && !bool(v, "commit")) error("PUSH_REQUIRES_COMMIT", "push", "Push requires commit");
        if (hasPush(v) && bool(v, "ignore-local-changes")) error("PUSH_WITH_IGNORED_CHANGES", "push", "Push is blocked with ignore-local-changes");
    }

    private Map<String, Object> defaults() {
        Map<String, Object> values = new HashMap<>();
        values.put("projects", List.of("*")); values.put("update-level", "MINOR");
        values.put("ambiguous-strategy", "COMMON_SOURCE"); values.put("version-info-ttl", "R1d,S10m");
        values.put("ignore-local-changes", false); values.put("commit", false); values.put("override-review", false);
        values.put("verbose", false); values.put("workspace-root", "target/scafix/workspaces"); values.put("poms-root", "target/scafix/poms");
        return values;
    }

    private void mergeWorkspace(Map<String, Object> values, String id, String workspaceRoot) {
        Path file = Path.of(workspaceRoot, id, "config.json");
        if (!Files.exists(file)) error("WORKSPACE_CONFIG_NOT_FOUND", "workspace", "Workspace config not found: " + file);
        mergeJson(values, file, "workspace");
    }

    private void mergeFile(Map<String, Object> values, Path file) {
        if (!Files.exists(file)) error("CONFIG_NOT_FOUND", "config", "Config file not found: " + file);
        mergeJson(values, file, "config");
    }

    private void mergeJson(Map<String, Object> values, Path file, String source) {
        try {
            JsonNode root = mapper.readTree(Files.readString(file));
            if (!root.isObject()) error("CONFIG_INVALID", "config", "Config must be a JSON object: " + file);
            root.fieldNames().forEachRemaining(name -> {
                String key = CONFIG_ALIASES.getOrDefault(name, name);
                if (!KNOWN.contains(key)) error("UNKNOWN_CONFIG_KEY", name, "Unknown config key: " + name);
            });
            root.fields().forEachRemaining(entry -> {
                String key = CONFIG_ALIASES.getOrDefault(entry.getKey(), entry.getKey());
                if (!key.equals("schemaVersion")) {
                    Object value = jsonValue(entry.getValue());
                    values.put(key, value);
                    if (key.equals("cve-override-update-level")) values.put("__" + source + "-cve-override-update-level", value);
                    if (key.equals("update-level")) values.put("__" + source + "-update-level", value);
                }
            });
        } catch (IOException exception) { error("CONFIG_INVALID", "config", "Unable to read config: " + file); }
    }

    private static String resolvedCveOverrideUpdateLevel(Map<String, Object> values, Map<String, Object> cliValues) {
        Object explicitCve = cliValues.get("cve-override-update-level");
        if (explicitCve != null) return String.valueOf(explicitCve);
        Object configCve = values.get("__config-cve-override-update-level");
        if (configCve != null) return String.valueOf(configCve);
        Object workspaceCve = values.get("__workspace-cve-override-update-level");
        if (workspaceCve != null) return String.valueOf(workspaceCve);
        Object explicitUpdate = cliValues.get("update-level");
        if (explicitUpdate != null) return String.valueOf(explicitUpdate);
        Object configUpdate = values.get("__config-update-level");
        if (configUpdate != null) return String.valueOf(configUpdate);
        Object workspaceUpdate = values.get("__workspace-update-level");
        if (workspaceUpdate != null) return String.valueOf(workspaceUpdate);
        return "PATCH";
    }

    private Object jsonValue(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isArray()) { List<String> result = new ArrayList<>(); node.forEach(item -> result.add(item.asText())); return result; }
        if (node.isBoolean()) return node.booleanValue();
        return node.asText();
    }

    private static CliCommand command(String value) { try { return CliCommand.valueOf(value.toUpperCase().replace('-', '_')); } catch (Exception e) { throw new CliConfigException("UNKNOWN_COMMAND", value, "Unknown command: " + value); } }
    private static String value(String token, String inline, List<String> args, int index) { if (inline != null) return inline; if (index >= args.size() || args.get(index).startsWith("--")) error("MISSING_VALUE", token, "Value is required for " + token); return args.get(index); }
    private static String string(Map<String, Object> v, String key) { Object value = v.get(key); return value == null ? null : String.valueOf(value); }
    private static String pushBranch(Map<String, Object> values) {
        String branch = string(values, "push");
        return branch == null || branch.isBlank() || branch.equalsIgnoreCase("false") ? null : branch;
    }
    private static Integer integer(Map<String, Object> v, String key) {
        String value = string(v, key);
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException exception) { error("INVALID_VALUE", key, "Invalid integer: " + value); return null; }
    }
    private static void proxy(Map<String, Object> v) {
        String host = string(v, "proxy-host");
        Integer port = integer(v, "proxy-port");
        boolean any = host != null || port != null || string(v, "proxy-username") != null || string(v, "proxy-password") != null;
        if (!any) return;
        if (host == null || port == null) error("PROXY_CONFIGURATION_INVALID", "proxy", "Proxy requires both proxy-host and proxy-port");
        if (port < 1 || port > 65535) error("PROXY_CONFIGURATION_INVALID", "proxy-port", "Proxy port out of range");
        if (string(v, "proxy-password") != null && string(v, "proxy-username") == null)
            error("PROXY_CONFIGURATION_INVALID", "proxy-username", "Proxy password requires proxy username");
    }
    private static boolean hasPush(Map<String, Object> values) {
        String branch = string(values, "push");
        return branch != null && !branch.isBlank() && !branch.equalsIgnoreCase("false");
    }
    private static boolean bool(Map<String, Object> v, String key) { return Boolean.parseBoolean(String.valueOf(v.getOrDefault(key, false))); }
    private static boolean booleanValue(String key, String value) { if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) error("INVALID_BOOLEAN", key, "Invalid boolean: " + value); return Boolean.parseBoolean(value); }
    private static List<String> list(Object value) { if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList(); return value == null ? List.of("*") : Arrays.stream(String.valueOf(value).split(",")).filter(s -> !s.isBlank()).toList(); }
    private static List<String> listOrEmpty(Object value) { if (value == null) return List.of(); return list(value); }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String parameter) { try { return Enum.valueOf(type, value == null ? "MINOR" : value.toUpperCase()); } catch (Exception e) { error("INVALID_VALUE", parameter, "Invalid value: " + value); return null; } }
    private static void error(String code, String parameter, String message) { throw new CliConfigException(code, parameter, message); }
}
