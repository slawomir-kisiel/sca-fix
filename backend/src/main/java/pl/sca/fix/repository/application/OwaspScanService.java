package pl.sca.fix.repository.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.sca.fix.common.process.ExternalProcessRunner;
import pl.sca.fix.integration.maven.MavenMetadataClient;
import pl.sca.fix.integration.maven.MavenProjectRepositoryResolver;
import pl.sca.fix.integration.maven.MavenVersionComparator;
import pl.sca.fix.integration.maven.MavenVersionLookup;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.project.application.PomDescriptor;
import pl.sca.fix.project.application.PomParser;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.repository.api.OwaspScanDtos.ScanPlanDto;
import pl.sca.fix.repository.api.OwaspScanDtos.ScanScopeDto;
import pl.sca.fix.repository.api.OwaspScanDtos.ScanStartResponse;
import pl.sca.fix.repository.api.OwaspScanDtos.ScopeJobDto;
import pl.sca.fix.report.api.ReportDtos.ReportImportResponseDto;
import pl.sca.fix.report.application.ReportCorrelationService;
import pl.sca.fix.report.application.ReportStorageService;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;

@Service
public class OwaspScanService {
    private static final Logger log = LoggerFactory.getLogger(OwaspScanService.class);
    private static final String GROUP = "org.owasp";
    private static final String ARTIFACT = "dependency-check-maven";
    private static final String FALLBACK_VERSION = "12.1.9";

    private final ProjectRepository projects;
    private final RepositoryRepository repositories;
    private final PomParser pomParser;
    private final MavenMetadataClient metadata;
    private final MavenProjectRepositoryResolver projectRepositories;
    private final JobService jobs;
    private final ExternalProcessRunner processRunner;
    private final ReportStorageService reportStorage;
    private final ReportCorrelationService correlation;
    private final Executor executor;
    private final String configuredVersion;
    private final String nvdApiKey;
    private final Path outputRoot;

    public OwaspScanService(ProjectRepository projects, RepositoryRepository repositories,
                            PomParser pomParser, MavenMetadataClient metadata,
                            MavenProjectRepositoryResolver projectRepositories, JobService jobs,
                            ExternalProcessRunner processRunner, ReportStorageService reportStorage,
                            ReportCorrelationService correlation,
                            @Qualifier("mavenAnalysisExecutor") Executor executor,
                            @Value("${sca.owasp.dependency-check-plugin-version:}") String configuredVersion,
                            @Value("${sca.owasp.nvd-api-key:}") String nvdApiKey,
                            @Value("${sca.owasp.output-root:./data/owasp-scans}") String outputRoot) {
        this.projects = projects;
        this.repositories = repositories;
        this.pomParser = pomParser;
        this.metadata = metadata;
        this.projectRepositories = projectRepositories;
        this.jobs = jobs;
        this.processRunner = processRunner;
        this.reportStorage = reportStorage;
        this.correlation = correlation;
        this.executor = executor;
        this.configuredVersion = configuredVersion == null ? "" : configuredVersion.trim();
        this.nvdApiKey = nvdApiKey == null ? "" : nvdApiKey.trim();
        this.outputRoot = Path.of(outputRoot).toAbsolutePath().normalize();
    }

    public ScanPlanDto plan(UUID repositoryId) {
        Repository repository = repository(repositoryId);
        List<Project> registered = projects.findByRepositoryIdOrderByPomPath(repositoryId);
        Path root = checkout(repository);
        Map<String, Project> byPom = new HashMap<>();
        Map<String, PomDescriptor> descriptors = new HashMap<>();
        for (Project project : registered) {
            byPom.put(normalize(project.getPomPath()), project);
            Path pom = root.resolve(project.getPomPath()).normalize();
            if (Files.isRegularFile(pom)) {
                try { descriptors.put(normalize(project.getPomPath()), pomParser.parse(root, pom)); }
                catch (RuntimeException exception) { log.warn("Unable to parse registered POM for OWASP scope path={}: {}", project.getPomPath(), exception.getMessage()); }
            }
        }
        List<String> aggregatorPaths = descriptors.entrySet().stream()
                .filter(entry -> !entry.getValue().modules().isEmpty())
                .map(Map.Entry::getKey).sorted(Comparator.comparingInt(this::depth).thenComparing(String::compareTo)).toList();
        Set<String> covered = new HashSet<>();
        List<ScanScopeDto> scopes = new ArrayList<>();
        for (String path : aggregatorPaths) {
            if (covered.contains(path)) continue;
            Set<String> members = moduleClosure(path, descriptors, byPom);
            members.add(path);
            covered.addAll(members);
            scopes.add(scope(path, members, byPom, true));
        }
        for (Project project : registered) {
            String path = normalize(project.getPomPath());
            if (!covered.contains(path)) {
                scopes.add(scope(path, Set.of(path), byPom, false));
                covered.add(path);
            }
        }
        scopes.sort(Comparator.comparing(ScanScopeDto::pomPath));
        VersionSelection version = pluginVersion(scopes);
        return new ScanPlanDto(repositoryId, version.selected(), version.latest(), scopes, version.warnings());
    }

    public ScanStartResponse start(UUID repositoryId, List<UUID> requestedScopeIds) {
        ScanPlanDto plan = plan(repositoryId);
        Set<UUID> selected = requestedScopeIds == null || requestedScopeIds.isEmpty()
                ? plan.scopes().stream().map(ScanScopeDto::scopeId).collect(java.util.stream.Collectors.toSet())
                : new HashSet<>(requestedScopeIds);
        List<ScopeJobDto> result = new ArrayList<>();
        for (ScanScopeDto scope : plan.scopes()) {
            if (!selected.contains(scope.scopeId())) continue;
            Job job = jobs.create("OWASP_DEPENDENCY_CHECK");
            result.add(new ScopeJobDto(scope.scopeId(), scope.rootProjectId(), job.getId(), job.getStatus().name(), scope.pomPath()));
            executor.execute(() -> run(job.getId(), repositoryId, scope, plan.pluginVersion()));
        }
        return new ScanStartResponse(repositoryId, plan.pluginVersion(), result);
    }

    private void run(UUID jobId, UUID repositoryId, ScanScopeDto scope, String pluginVersion) {
        AtomicLong sequence = new AtomicLong();
        Path output = outputRoot.resolve(repositoryId.toString()).resolve(jobId.toString()).normalize();
        try {
            Repository repository = repository(repositoryId);
            Path root = checkout(repository);
            Path pom = root.resolve(scope.pomPath()).normalize();
            Files.createDirectories(output);
            List<String> command = command(pom, pluginVersion, output);
            jobs.start(jobId, "OWASP_DEPENDENCY_CHECK");
            jobs.appendCommand(jobId, sequence.incrementAndGet(), command);
            int exit = processRunner.run(jobId, command, root,
                    line -> jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDOUT", line),
                    line -> jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDERR", line)).join();
            if (exit != 0) throw new IllegalStateException("OWASP dependency-check failed with exit code " + exit);
            Path report = findReport(output);
            ReportImportResponseDto imported = reportStorage.store(report, repositoryId,
                    "dependency-check-report-" + jobId + ".json", "application/json");
            jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDOUT", "Imported report " + imported.reportId());
            correlation.start(imported.reportId());
            jobs.finish(jobId, JobStatus.SUCCESS, 0, null, null);
            log.info("OWASP dependency-check completed jobId={} repositoryId={} scope={} reportId={}", jobId, repositoryId, scope.pomPath(), imported.reportId());
        } catch (Exception exception) {
            log.error("OWASP dependency-check failed jobId={} repositoryId={} scope={}", jobId, repositoryId, scope.pomPath(), exception);
            jobs.finish(jobId, JobStatus.FAILED, null, "OWASP_SCAN_FAILED", exception.getMessage());
        }
    }

    private List<String> command(Path pom, String pluginVersion, Path output) {
        List<String> command = new ArrayList<>(List.of("mvn", "-f", pom.toString(), GROUP + ":" + ARTIFACT + ":" + pluginVersion + ":aggregate",
                "-Dformat=JSON", "-DoutputDirectory=" + output, "-DfailOnError=false",
                "-DnvdApiServerId=nvd", "-DossIndexServerId=ossindex"));
        if (!nvdApiKey.isBlank()) command.add("-DnvdApiKey=" + nvdApiKey);
        return command;
    }

    private Path findReport(Path output) throws IOException {
        Path expected = output.resolve("dependency-check-report.json");
        if (Files.isRegularFile(expected)) return expected;
        try (var paths = Files.walk(output)) {
            return paths.filter(path -> Files.isRegularFile(path) && "dependency-check-report.json".equals(path.getFileName().toString()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("OWASP JSON report was not generated"));
        }
    }

    private VersionSelection pluginVersion(List<ScanScopeDto> scopes) {
        List<String> warnings = new ArrayList<>();
        String latest = null;
        if (!scopes.isEmpty()) {
            Project root = projects.findById(scopes.get(0).rootProjectId()).orElse(null);
            MavenVersionLookup lookup = metadata.findVersions(GROUP, ARTIFACT, null,
                    projectRepositories.resolve(root));
            warnings.addAll(lookup.warnings());
            latest = lookup.versions().stream().map(MavenVersionLookup.RemoteVersion::version)
                    .filter(this::isMajor12).max(MavenVersionComparator::compareVersions).orElse(null);
        }
        String selected = configuredVersion;
        if (!selected.isBlank() && !isMajor12(selected)) {
            warnings.add("Configured OWASP plugin version is outside major 12: " + selected);
            selected = "";
        }
        if (selected.isBlank()) selected = latest != null ? latest : FALLBACK_VERSION;
        if (latest == null) warnings.add("OWASP plugin metadata unavailable; using " + selected);
        return new VersionSelection(selected, latest, warnings);
    }

    private boolean isMajor12(String version) { return version != null && version.matches("12(?:[.-].*|)"); }

    private Set<String> moduleClosure(String path, Map<String, PomDescriptor> descriptors, Map<String, Project> byPom) {
        Set<String> result = new LinkedHashSet<>();
        for (String module : descriptors.getOrDefault(path, new PomDescriptor(path, null, null, null, null, null, List.of())).modules()) {
            String child = modulePath(path, module);
            if (!byPom.containsKey(child) || !result.add(child)) continue;
            result.addAll(moduleClosure(child, descriptors, byPom));
        }
        return result;
    }

    private String modulePath(String parent, String module) {
        Path base = Path.of(parent).getParent();
        Path candidate = (base == null ? Path.of(module) : base.resolve(module)).normalize();
        String value = normalize(candidate.toString());
        if (!value.endsWith(".xml")) value = value + "/pom.xml";
        return normalize(value);
    }

    private ScanScopeDto scope(String path, Set<String> members, Map<String, Project> byPom, boolean aggregator) {
        Project root = byPom.get(path);
        List<String> paths = members.stream().sorted().toList();
        return new ScanScopeDto(root.getId(), root.getId(), root.getName(), root.getPomPath(), root.getGroupId(),
                root.getArtifactId(), root.getVersion(), aggregator, paths.stream().map(byPom::get).filter(java.util.Objects::nonNull).map(Project::getId).toList(), paths);
    }

    private Repository repository(UUID id) { return repositories.findById(id).orElseThrow(() -> new RepositoryNotFoundException(id)); }
    private Path checkout(Repository repository) { return Path.of(repository.getClonePath()).toAbsolutePath().normalize(); }
    private String normalize(String path) { return path == null ? "" : path.replace('\\', '/').replaceFirst("^\\./", ""); }
    private int depth(String path) { return path.isBlank() ? 0 : path.split("/").length; }
    private record VersionSelection(String selected, String latest, List<String> warnings) { }
}
