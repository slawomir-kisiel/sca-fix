package pl.sca.fix.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliConfigurationParserTest {
    private final CliConfigurationParser parser = new CliConfigurationParser();

    @TempDir Path temp;

    @Test
    void parsesScanDefaultsAndExplicitValues() {
        CliConfiguration result = parser.parse("scan", "--repository", "checkout", "--report-source", "file",
                "--report-input", "report.json", "--projects", "com.test:a,com.test:b", "--update-level", "PATCH");

        assertEquals(CliCommand.SCAN, result.command());
        assertEquals(ScanMode.NEW, result.scanMode());
        assertEquals("checkout", result.repository());
        assertEquals(java.util.List.of("com.test:a", "com.test:b"), result.projects());
        assertEquals("PATCH", result.updateLevel().name());
        assertEquals("R1d,S10m", result.versionInfoTtl());
    }

    @Test
    void parsesOwaspReportOptionsAndUsesSafeDefaults() {
        CliConfiguration result = parser.parse("scan", "--repository", "checkout", "--report-source", "owasp",
                "--nvd-api-key", "secret", "--dependency-check-version", "12.1.9");

        assertEquals("owasp", result.reportSource());
        assertEquals("secret", result.nvdApiKey());
        assertEquals("12.1.9", result.dependencyCheckVersion());
        assertEquals("nvd", result.nvdApiServerId());
        assertEquals("ossindex", result.ossIndexServerId());
        assertEquals("json", result.reportFormat());
    }

    @Test
    void rejectsRemovedGenerateReportSource() {
        CliConfigException exception = assertThrows(CliConfigException.class,
                () -> parser.parse("scan", "--repository", "checkout", "--report-source", "generate"));
        assertEquals("INVALID_REPORT_SOURCE", exception.code());
    }

    @Test
    void appliesWorkspaceThenConfigThenCliPrecedence() throws Exception {
        Path root = temp.resolve("workspaces");
        Path workspace = root.resolve("ws-1");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("config.json"), "{\"repository\":\"workspace\",\"update-level\":\"MAJOR\"}");
        Path config = temp.resolve("config.json");
        Files.writeString(config, "{\"repository\":\"file\",\"update-level\":\"PATCH\"}");

        CliConfiguration result = parser.parse("scan", "--continue", "ws-1", "--config", config.toString(),
                "--workspace-root", root.toString(), "--repository", "cli", "--report-source", "file",
                "--report-input", "report.json");

        assertEquals("cli", result.repository());
        assertEquals("PATCH", result.updateLevel().name());
    }

    @Test
    void resolvesCveOverrideLevelWithItsDedicatedPrecedence() throws Exception {
        Path root = temp.resolve("workspaces");
        Path workspace = root.resolve("ws-cve");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("config.json"), "{\"repository\":\"workspace\",\"updateLevel\":\"MINOR\",\"cveOverrideUpdateLevel\":\"MAJOR\"}");
        Path config = temp.resolve("cve-config.json");
        Files.writeString(config, "{\"updateLevel\":\"PATCH\",\"cveOverrideUpdateLevel\":\"MINOR\"}");

        CliConfiguration fromConfig = parser.parse("scan", "--continue", "ws-cve", "--config", config.toString(),
                "--workspace-root", root.toString(), "--repository", "checkout", "--report-source", "file",
                "--report-input", "report.json");
        assertEquals("MINOR", fromConfig.cveOverrideUpdateLevel().name());

        CliConfiguration fromCliUpdate = parser.parse("scan", "--continue", "ws-cve", "--config", config.toString(),
                "--workspace-root", root.toString(), "--repository", "checkout", "--report-source", "file",
                "--report-input", "report.json", "--update-level", "MAJOR");
        assertEquals("MINOR", fromCliUpdate.cveOverrideUpdateLevel().name());

        CliConfiguration explicit = parser.parse("scan", "--repository", "checkout", "--report-source", "file",
                "--report-input", "report.json", "--update-level", "MAJOR",
                "--cve-override-update-level", "PATCH");
        assertEquals("PATCH", explicit.cveOverrideUpdateLevel().name());
    }

    @Test
    void defaultsCveOverrideLevelToPatch() {
        CliConfiguration result = parser.parse("scan", "--repository", "checkout", "--report-source", "file",
                "--report-input", "report.json");
        assertEquals("PATCH", result.cveOverrideUpdateLevel().name());
    }

    @Test
    void validatesApplyPlanSourceAndPushRules() {
        CliConfigException missing = assertThrows(CliConfigException.class,
                () -> parser.parse("apply", "--repository", "checkout"));
        assertEquals("PLAN_SOURCE_CONFLICT", missing.code());

        CliConfigException push = assertThrows(CliConfigException.class,
                () -> parser.parse("apply", "--plan", "plan.json", "--push", "branch"));
        assertEquals("PUSH_REQUIRES_COMMIT", push.code());
    }

    @Test
    void supportsStandaloneOverrideReviewWithoutReport() {
        CliConfiguration result = parser.parse("override-review", "--repository", "checkout");
        assertEquals(CliCommand.OVERRIDE_REVIEW, result.command());
        assertFalse(result.overrideReview());
        assertTrue(result.projects().contains("*"));
    }

    @Test
    void rejectsReportForStandaloneOverrideReview() {
        CliConfigException exception = assertThrows(CliConfigException.class,
                () -> parser.parse("override-review", "--report-input", "report.json"));
        assertEquals("REPORT_NOT_ALLOWED", exception.code());
    }

    @Test
    void explicitProxyParametersOverrideConfigFile() throws Exception {
        Path config = temp.resolve("proxy.json");
        Files.writeString(config, "{\"proxyHost\":\"config.proxy\",\"proxyPort\":8080}");
        CliConfiguration result = parser.parse("override-review", "--repository", "checkout",
                "--config", config.toString(), "--proxy-host", "cli.proxy", "--proxy-port", "3128");
        assertEquals("cli.proxy", result.proxyHost());
        assertEquals(3128, result.proxyPort());
    }

    @Test
    void scanCanApplyAndPublishAfterPlanning() {
        CliConfiguration result = parser.parse("scan", "--repository", "checkout", "--report-source", "file",
                "--report-input", "report.json", "--apply", "--commit", "--push", "fix-branch");
        assertTrue(result.apply());
        assertTrue(result.commit());
        assertEquals("fix-branch", result.pushBranch());
    }

    @Test
    void acceptsDisabledStrategiesOnlyForApply() {
        CliConfiguration result = parser.parse("scan", "--repository", "checkout", "--report-source", "file",
                "--report-input", "report.json", "--apply", "--disable-strategy", "cve_override,direct_dependency");
        assertEquals(java.util.List.of("CVE_OVERRIDE", "DIRECT_DEPENDENCY"), result.disabledStrategies());

        CliConfigException exception = assertThrows(CliConfigException.class,
                () -> parser.parse("scan", "--repository", "checkout", "--report-source", "file",
                        "--report-input", "report.json", "--disable-strategy", "CVE_OVERRIDE"));
        assertEquals("DISABLE_STRATEGY_NOT_ALLOWED", exception.code());
    }

    @Test
    void parsesSkipLibraryDescriptors() {
        CliConfiguration result = parser.parse("apply", "--plan", "plan.json", "--repository", "checkout",
                "--skip", "com.google.guava,org.apache:collections4,org.wso2:test-unit:3.");

        assertEquals(java.util.List.of("com.google.guava", "org.apache:collections4", "org.wso2:test-unit:3."),
                result.skipLibraries());
    }

    @Test
    void rejectsMalformedSkipLibraryDescriptor() {
        CliConfigException exception = assertThrows(CliConfigException.class,
                () -> parser.parse("apply", "--plan", "plan.json", "--repository", "checkout", "--skip", "org:a:b:c"));
        assertEquals("INVALID_VALUE", exception.code());
    }

    @Test
    void readsDisabledStrategiesFromWorkspaceSnapshot() throws Exception {
        Path root = temp.resolve("workspaces");
        Path workspace = root.resolve("ws-1");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("config.json"), "{\"repository\":\"checkout\","
                + "\"reportAvailable\":true,\"apply\":true,"
                + "\"disabledStrategies\":[\"CVE_OVERRIDE\"]}");

        CliConfiguration result = parser.parse("scan", "--continue", "ws-1", "--workspace-root", root.toString());

        assertEquals(java.util.List.of("CVE_OVERRIDE"), result.disabledStrategies());
    }

    @Test
    void treatsNullProxyPortInConfigAsUnset() throws Exception {
        Path config = temp.resolve("null-proxy.json");
        Files.writeString(config, "{\"proxyHost\":null,\"proxyPort\":null}");

        CliConfiguration result = parser.parse("override-review", "--repository", "checkout",
                "--config", config.toString());

        assertEquals(null, result.proxyHost());
        assertEquals(null, result.proxyPort());
    }

    @Test
    void falsePushInWorkspaceConfigDoesNotActivatePush() throws Exception {
        Path root = temp.resolve("workspaces");
        Path workspace = root.resolve("ws-push");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("config.json"), "{\"repository\":\"checkout\","
                + "\"report-source\":\"file\",\"report-input\":\"report.json\","
                + "\"overrideReview\":true,\"push\":false}");

        CliConfiguration result = parser.parse("scan", "--continue", "ws-push", "--workspace-root", root.toString());

        assertEquals(null, result.pushBranch());
    }
}
