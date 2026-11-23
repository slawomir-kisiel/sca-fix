package pl.sca.fix.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.discovery.ProjectDiscoveryRequest;
import pl.sca.fix.core.discovery.ProjectDiscoveryResult;
import pl.sca.fix.core.discovery.ProjectDiscoveryService;
import pl.sca.fix.core.discovery.MavenPomParser;
import pl.sca.fix.core.git.GitProcessAdapter;
import pl.sca.fix.core.maven.DependencyTreeFileWriter;
import pl.sca.fix.core.maven.DependencyTreeTextWriter;
import pl.sca.fix.core.maven.EffectivePomCache;
import pl.sca.fix.core.maven.EffectivePomCacheRequest;
import pl.sca.fix.core.maven.EffectivePomJsonCodec;
import pl.sca.fix.core.maven.JsonDependencyTreeParser;
import pl.sca.fix.core.maven.MavenMetadataClient;
import pl.sca.fix.core.maven.MavenSettingsProxyResolver;
import pl.sca.fix.core.maven.MavenProcessAdapter;
import pl.sca.fix.core.maven.MavenRepositorySpec;
import pl.sca.fix.core.maven.MavenToolConfiguration;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.maven.SystemProcessRunner;
import pl.sca.fix.core.maven.VersionCacheDocument;
import pl.sca.fix.core.maven.VersionCacheService;
import pl.sca.fix.core.maven.VersionInfoTtl;
import pl.sca.fix.core.model.ProjectModel;
import pl.sca.fix.core.model.ArtifactCoordinate;
import pl.sca.fix.core.plan.AmbiguousStrategy;
import pl.sca.fix.core.plan.AutomaticPlanningResult;
import pl.sca.fix.core.plan.AutomaticPlanningService;
import pl.sca.fix.core.plan.OverrideReviewResult;
import pl.sca.fix.core.plan.OverrideReviewService;
import pl.sca.fix.core.plan.PlanApplyRequest;
import pl.sca.fix.core.plan.PlanApplyResult;
import pl.sca.fix.core.plan.PlanDocument;
import pl.sca.fix.core.plan.PlanDocumentJsonCodec;
import pl.sca.fix.core.plan.PlanningProject;
import pl.sca.fix.core.pipeline.PipelineStageExecutor;
import pl.sca.fix.core.pipeline.PipelineStageResult;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.MavenDependencyTreeRequest;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.VersionLookupRequest.ProxySettings;
import pl.sca.fix.core.port.ProcessRunnerPort;
import pl.sca.fix.core.port.ReportDocument;
import pl.sca.fix.core.report.CanonicalReportStore;
import pl.sca.fix.core.report.CanonicalScanReport;
import pl.sca.fix.core.report.CorrelationJsonCodec;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.CorrelationService;
import pl.sca.fix.core.report.ProjectTree;
import pl.sca.fix.core.report.ReportImportResult;
import pl.sca.fix.core.report.ScanReportImporter;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.workspace.WorkspaceState;

/**
 * Production stage executor used by the standalone CLI.  It deliberately
 * composes the existing core services instead of reimplementing their rules.
 */
final class CliRuntimeStageExecutor implements PipelineStageExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(CliRuntimeStageExecutor.class);
    private static final String CENTRAL = "https://repo.maven.apache.org/maven2";

    private final CliConfiguration configuration;
    private final FileSystemPort files = new NioFileSystemAdapter();
    private final GitPort git;
    private final MavenProcessAdapter maven;
    private final ProjectDiscoveryService discovery;
    private final EffectivePomCache effectivePoms;
    private final ScanReportImporter importer = new ScanReportImporter();
    private final CorrelationService correlationService = new CorrelationService();
    private final PlanDocumentJsonCodec planCodec = new PlanDocumentJsonCodec();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ProjectDiscoveryResult discovered;
    private final List<ProjectTree> projectTrees = new ArrayList<>();
    private final List<PlanningProject> planningProjects = new ArrayList<>();
    private CanonicalScanReport report;
    private CorrelationResult correlation;
    private PlanDocument plan;

    CliRuntimeStageExecutor(CliConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        ProcessRunnerPort processes = new SystemProcessRunner();
        this.git = new GitProcessAdapter(processes);
        MavenToolConfiguration tools = MavenToolConfiguration.defaults(isWindows());
        this.maven = new MavenProcessAdapter(processes, files, tools);
        this.discovery = new ProjectDiscoveryService(git, files);
        this.effectivePoms = new EffectivePomCache(files, maven);
    }

    @Override
    public PipelineStageResult execute(WorkspaceState.Stage stage, WorkspaceState state) {
        LOG.info("Executing runtime pipeline stage workspaceId={} stage={}", state.workspaceId(), stage);
        return switch (stage) {
            case DISCOVERY -> discover(state);
            case MAVEN_ANALYSIS -> analyseMaven(state);
            case REPORT -> importReport(state);
            case CORRELATION -> correlate(state);
            case OVERRIDE_REVIEW -> overrideReview(state);
            case PLANNING -> plan(state);
            case APPLY -> apply(state);
            case PUBLISH -> publish(state);
            case SUMMARY -> summary(state);
        };
    }

    private PipelineStageResult discover(WorkspaceState state) {
        discovered = discovery.discover(ProjectDiscoveryRequest.cli(repository(), configuration.projects()));
        List<String> artifacts = List.of(path(state, "discovery.json").toString());
        writeJson(path(state, "discovery.json"), discovered.projects().stream().map(project -> Map.of(
                "groupId", project.coordinate().groupId(), "artifactId", project.coordinate().artifactId(),
                "version", project.coordinate().version(), "pomPath", project.pomPath().toString(),
                "packaging", project.packaging())).toList());
        LOG.info("Discovered Maven projects workspaceId={} projects={}", state.workspaceId(), discovered.projects().size());
        return PipelineStageResult.completed(artifacts.toArray(String[]::new));
    }

    private PipelineStageResult analyseMaven(WorkspaceState state) {
        ensureDiscovered();
        projectTrees.clear();
        planningProjects.clear();
        List<String> artifacts = new ArrayList<>();
        Path workspace = workspace(state);
        for (ProjectModel project : discovered.projects()) {
            Path pom = repository().resolve(project.pomPath()).normalize();
            String key = project.coordinate().groupId() + ":" + project.coordinate().artifactId()
                    + ":" + project.coordinate().version();
            Path projectOutput = workspace.resolve("projects").resolve(safeFileName(key));
            Path treeOutput = projectOutput.resolve("dependency-tree.json");
            EffectivePomCache.Loaded loaded = effectivePoms.load(new EffectivePomCacheRequest(
                    repository(), pom, configuration.pomsRoot(), false));
            var treeResult = maven.generateDependencyTree(new MavenDependencyTreeRequest(
                    pom, treeOutput, null, false));
            projectTrees.add(new ProjectTree(key, pom, new JsonDependencyTreeParser(mapper, files)
                    .parse(treeResult.outputFile(), project.coordinate())));
            planningProjects.add(new PlanningProject(key, pom, loaded.model(), true));
            artifacts.add(loaded.paths().json().toString());
            artifacts.add(treeOutput.toString());
        }
        LOG.info("Maven analysis finished workspaceId={} projects={}", state.workspaceId(), projectTrees.size());
        return new PipelineStageResult(artifacts, List.of(), List.of());
    }

    private PipelineStageResult importReport(WorkspaceState state) {
        Path input = reportInput(state);
        if (!Files.isRegularFile(input)) return failure("REPORT_NOT_FOUND", "Report file not found: " + input);
        ReportDocument document = new ReportDocument(read(input), input, reportHint());
        ReportImportResult result = importer.importReport(document);
        report = result.report();
        Path output = path(state, "report.json");
        new CanonicalReportStore(files).save(output, report);
        if (!result.errors().isEmpty()) {
            return new PipelineStageResult(List.of(output.toString()), issues(result.warnings(), "REPORT"),
                    result.errors().stream().map(message -> issue("REPORT_IMPORT_FAILED", message, "REPORT")).toList());
        }
        return new PipelineStageResult(List.of(output.toString()),
                issues(result.warnings(), "REPORT"), List.of());
    }

    private PipelineStageResult correlate(WorkspaceState state) {
        ensureReport(state);
        ensureTrees(state);
        correlation = correlationService.correlate(report, projectTrees, configuration.updateLevel());
        Path output = path(state, "correlations.json");
        files.writeAtomically(output, new CorrelationJsonCodec().write(correlation));
        return new PipelineStageResult(List.of(output.toString()),
                correlation.warnings().stream().map(message -> issue("CORRELATION_WARNING", message, "CORRELATION")).toList(),
                correlation.errors().stream().map(message -> issue("CORRELATION_FAILED", message, "CORRELATION")).toList());
    }

    private PipelineStageResult overrideReview(WorkspaceState state) {
        ensurePlanningProjects(state);
        OverrideReviewService service = new OverrideReviewService(files, maven, workspace(state).resolve("override-review"));
        OverrideReviewResult result = service.review(planningProjects, repository(), commitSha(), configuration.updateLevel());
        // Disabled strategies affect execution only. The generated plan must remain complete
        // so it can be reviewed and applied again with a different decision.
        plan = result.plan();
        Path output = path(state, "plan.json");
        files.writeAtomically(output, planCodec.write(plan));
        return new PipelineStageResult(List.of(output.toString()),
                result.plan().warnings().stream().map(item -> issue(item.reason(), item.message(), "OVERRIDE_REVIEW")).toList(),
                result.plan().errors().stream().map(item -> issue(item.reason(), item.message(), "OVERRIDE_REVIEW")).toList());
    }

    private PipelineStageResult plan(WorkspaceState state) {
        ensureReport(state);
        ensureTrees(state);
        ensureCorrelation(state);
        ensurePlanningProjects(state);
        Map<String, List<VersionCacheDocument.VersionEntry>> versions = new LinkedHashMap<>();
        VersionCacheService cache = new VersionCacheService(files, new MavenMetadataClient(),
                () -> Instant.now(Clock.systemUTC()), configuration.pomsRoot().resolveSibling("versions"),
                Duration.ofMinutes(10), proxy());
        VersionInfoTtl ttl = VersionInfoTtl.parse(configuration.versionInfoTtl());
        List<MavenRepositorySpec> repositories = List.of(new MavenRepositorySpec("central", CENTRAL, true, true));
        for (var library : report.vulnerableLibraries()) {
            var result = cache.findVersions(library.groupId(), library.artifactId(), repositories, ttl, Duration.ofSeconds(30));
            versions.put(library.groupId() + ":" + library.artifactId(), result.versions());
        }
        AutomaticPlanningResult result = new AutomaticPlanningService().plan(report, correlation, planningProjects,
                versions, repository(), commitSha(), configuration.updateLevel(),
                configuration.cveOverrideUpdateLevel(),
                AmbiguousStrategy.parse(configuration.ambiguousStrategy()), configuration.overrideReview());
        plan = result.plan();
        Path output = path(state, "plan.json");
        files.writeAtomically(output, planCodec.write(plan));
        return new PipelineStageResult(List.of(output.toString()),
                plan.warnings().stream().map(item -> issue(item.reason(), item.message(), "PLANNING")).toList(),
                plan.errors().stream().map(item -> issue(item.reason(), item.message(), "PLANNING")).toList());
    }

    private PipelineStageResult apply(WorkspaceState state) {
        Path planPath = configuration.plan() == null ? path(state, "plan.json") : Path.of(configuration.plan());
        if (!Files.isRegularFile(planPath)) return failure("PLAN_NOT_FOUND", "Plan file not found: " + planPath);
        plan = planCodec.read(read(planPath));
        PlanApplyResult result = new pl.sca.fix.core.plan.PlanApplyService(files, git)
                .apply(new PlanApplyRequest(plan, repository(), configuration.ignoreLocalChanges(), false, false,
                        configuration.disabledStrategies(), configuration.skipLibraries()));
        plan = result.plan();
        Path output = path(state, "plan.json");
        files.writeAtomically(output, planCodec.write(plan));
        files.writeAtomically(path(state, "diff.patch"), result.summary().diff().getBytes(StandardCharsets.UTF_8));
        writeJson(path(state, "apply-summary.json"), result.summary());
        List<WorkspaceState.Issue> errors = result.summary().errors().stream()
                .map(item -> issue(item.reason(), item.message(), "APPLY")).toList();
        return new PipelineStageResult(List.of(output.toString(), path(state, "diff.patch").toString()), List.of(), errors);
    }

    private PipelineStageResult publish(WorkspaceState state) {
        if (plan == null) {
            Path planPath = path(state, "plan.json");
            if (Files.isRegularFile(planPath)) plan = planCodec.read(read(planPath));
        }
        if (plan == null) return failure("PLAN_NOT_FOUND", "No plan available for publish");
        List<Path> poms = plan.items().stream().map(item -> repository().resolve(item.project().pomPath()).normalize())
                .distinct().toList();
        git.add(repository(), poms);
        String commit = git.commit(repository(), "Apply SCA fix plan " + plan.planId());
        if (configuration.pushBranch() != null && !configuration.pushBranch().isBlank()) {
            git.push(repository(), configuration.pushBranch());
        }
        files.writeAtomically(path(state, "published-commit.txt"), commit.getBytes(StandardCharsets.UTF_8));
        return PipelineStageResult.completed(path(state, "published-commit.txt").toString());
    }

    private PipelineStageResult summary(WorkspaceState state) {
        return PipelineStageResult.completed(path(state, "summary.txt").toString());
    }

    private void ensureDiscovered() {
        if (discovered == null) discovered = discovery.discover(ProjectDiscoveryRequest.cli(repository(), configuration.projects()));
    }

    private void ensureTrees(WorkspaceState state) {
        if (!projectTrees.isEmpty()) return;
        Path discoveryFile = path(state, "discovery.json");
        if (Files.isRegularFile(discoveryFile)) {
            try {
                var entries = mapper.readTree(read(discoveryFile));
                for (var entry : entries) {
                    String groupId = entry.path("groupId").asText();
                    String artifactId = entry.path("artifactId").asText();
                    String version = entry.path("version").asText();
                    String pomText = entry.path("pomPath").asText();
                    ArtifactCoordinate coordinate = new ArtifactCoordinate(groupId, artifactId, version);
                    String key = coordinate.gav();
                    Path output = workspace(state).resolve("projects").resolve(safeFileName(key))
                            .resolve("dependency-tree.json");
                    if (!Files.isRegularFile(output)) continue;
                    Path pom = Path.of(pomText);
                    if (!pom.isAbsolute()) pom = repository().resolve(pom);
                    projectTrees.add(new ProjectTree(key, pom, new JsonDependencyTreeParser(mapper, files)
                            .parse(output, coordinate)));
                }
            } catch (Exception exception) {
                throw new PortException(PortException.Kind.CONFLICT,
                        "Unable to restore Maven dependency trees from workspace", exception);
            }
        }
        if (projectTrees.isEmpty()) throw new PortException(PortException.Kind.CONFLICT,
                "Maven dependency trees are not available; run Maven analysis first");
    }

    private void ensureReport(WorkspaceState state) {
        if (report != null) return;
        Path file = path(state, "report.json");
        if (Files.isRegularFile(file)) report = new CanonicalReportStore(files).load(file);
        else throw new PortException(PortException.Kind.NOT_FOUND, "Canonical report not found: " + file);
    }

    private void ensureCorrelation(WorkspaceState state) {
        if (correlation != null) return;
        Path file = path(state, "correlations.json");
        if (Files.isRegularFile(file)) correlation = new CorrelationJsonCodec().read(read(file));
        else throw new PortException(PortException.Kind.NOT_FOUND, "Correlation result not found: " + file);
    }

    private void ensurePlanningProjects(WorkspaceState state) {
        if (planningProjects.isEmpty()) {
            ensureDiscovered();
            for (ProjectModel project : discovered.projects()) {
                Path pom = repository().resolve(project.pomPath()).normalize();
                Path json = configuration.pomsRoot().resolve(project.coordinate().groupId())
                        .resolve(project.coordinate().artifactId()).resolve(project.coordinate().version()).resolve("effective-pom.json");
                if (Files.isRegularFile(json)) {
                    planningProjects.add(new PlanningProject(project.coordinate().gav(), pom,
                            new EffectivePomJsonCodec(files).read(json), true));
                }
            }
            if (planningProjects.isEmpty()) analyseMaven(state);
        }
    }

    private Path reportInput(WorkspaceState state) {
        if (configuration.reportInput() != null) return Path.of(configuration.reportInput()).toAbsolutePath().normalize();
        return path(state, "report.json");
    }

    private String reportHint() {
        return configuration.reportSource() == null ? null : configuration.reportSource();
    }

    private Path repository() { return Path.of(configuration.repository()).toAbsolutePath().normalize(); }
    private String commitSha() { return git.currentCommitSha(repository()); }
    private ProxySettings proxy() {
        if (configuration.proxyHost() != null) {
            return new ProxySettings(configuration.proxyHost(), configuration.proxyPort(),
                    configuration.proxyUsername(), configuration.proxyPassword());
        }
        return new MavenSettingsProxyResolver().resolve().orElse(null);
    }

    private Path workspace(WorkspaceState state) { return configuration.workspaceRoot().resolve(state.workspaceId()).toAbsolutePath().normalize(); }
    private Path path(WorkspaceState state, String name) { return workspace(state).resolve(name); }
    private byte[] read(Path path) { return files.readBytes(path); }

    private void writeJson(Path path, Object value) {
        try { files.writeAtomically(path, mapper.writeValueAsBytes(value)); }
        catch (Exception exception) { throw new PortException(PortException.Kind.IO, "Unable to write JSON: " + path, exception); }
    }

    private static String safeFileName(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    private PipelineStageResult failure(String code, String message) {
        return new PipelineStageResult(List.of(), List.of(), List.of(issue(code, message, null)));
    }

    private static WorkspaceState.Issue issue(String code, String message, String stage) {
        return new WorkspaceState.Issue(code, message, stage, Instant.now());
    }

    private static List<WorkspaceState.Issue> issues(List<String> messages, String stage) {
        return messages == null ? List.of() : messages.stream()
                .map(message -> issue("WARNING", message, stage)).toList();
    }
}
