package pl.sca.fix.dependency.application;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import pl.sca.fix.common.process.ExternalProcessRunner;
import pl.sca.fix.dependency.api.DependencyGraphDtos.DependencyAnalysisResponse;
import pl.sca.fix.dependency.api.DependencyGraphDtos.BatchDependencyAnalysisResponse;
import pl.sca.fix.integration.maven.MavenCoordinate;
import pl.sca.fix.integration.maven.MavenDependencyTreeParser;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;
import pl.sca.fix.report.domain.ReportStatus;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DependencyAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(DependencyAnalysisService.class);
    private final ProjectRepository projects;
    private final RepositoryRepository repositories;
    private final ExternalProcessRunner processRunner;
    private final MavenDependencyTreeParser parser;
    private final ArtifactTreeService graph;
    private final JobService jobs;
    private final SecurityScanReportRepository reports;
    private final Executor executor;
    private final String dependencyPluginVersion;
    /**
     * Replacing a project's tree is a delete-and-recreate operation. Maven
     * analyses for different projects may still run in parallel, but two
     * replacements for the same project must not overlap.
     */
    private final ConcurrentHashMap<UUID, Object> graphReplacementLocks = new ConcurrentHashMap<>();

    public DependencyAnalysisService(ProjectRepository projects, RepositoryRepository repositories,
                                     ExternalProcessRunner processRunner, MavenDependencyTreeParser parser,
                                     ArtifactTreeService graph, JobService jobs, SecurityScanReportRepository reports,
                                     @Qualifier("mavenAnalysisExecutor") Executor executor,
                                     @Value("${sca.maven.dependency-plugin-version:3.8.1}") String dependencyPluginVersion) {
        this.projects = projects; this.repositories = repositories; this.processRunner = processRunner;
        this.parser = parser; this.graph = graph; this.jobs = jobs; this.reports = reports; this.executor = executor;
        this.dependencyPluginVersion = dependencyPluginVersion;
    }

    public DependencyAnalysisResponse start(UUID projectId) {
        return start(projectId, null);
    }

    public DependencyAnalysisResponse start(UUID projectId, UUID reportId) {
        Project project = projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (project.hasActiveMavenAnalysis()) {
            return new DependencyAnalysisResponse(project.getMavenAnalysisJobId(), "SKIPPED");
        }
        Job job = jobs.create("MAVEN_DEPENDENCY_ANALYSIS");
        project.queueMavenAnalysis(job.getId());
        projects.save(project);
        log.info("Scheduling Maven dependency analysis projectId={} jobId={} pomPath={} repositoryId={}", projectId, job.getId(), project.getPomPath(), project.getRepositoryId());
        executor.execute(() -> analyse(job.getId(), projectId, reportId));
        return new DependencyAnalysisResponse(job.getId(), job.getStatus().name());
    }

    public BatchDependencyAnalysisResponse startBatch(UUID repositoryId, List<UUID> projectIds) {
        List<DependencyAnalysisResponse> jobsStarted = new ArrayList<>();
        List<UUID> skipped = new ArrayList<>();
        for (UUID projectId : projectIds) {
            Project project = projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
            if (!repositoryId.equals(project.getRepositoryId()) || project.hasActiveMavenAnalysis()) {
                skipped.add(projectId);
                continue;
            }
            jobsStarted.add(start(projectId));
        }
        return new BatchDependencyAnalysisResponse(jobsStarted, skipped);
    }

    private void analyse(UUID jobId, UUID projectId, UUID reportId) {
        AtomicLong sequence = new AtomicLong();
        Project project = projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        try {
            Repository repository = repositories.findById(project.getRepositoryId()).orElseThrow();
            Path root = Path.of(repository.getClonePath()).toAbsolutePath().normalize();
            Path pom = root.resolve(project.getPomPath()).normalize();
            log.info("Running Maven dependency analysis jobId={} projectId={} pom={} root={}", jobId, project.getId(), pom, root);
            jobs.start(jobId, "DEPENDENCY_TREE");
            markAnalyzing(projectId);
            StringBuilder treeOutput = new StringBuilder();
            List<String> treeCommand = mavenCommand(pom, "dependency:tree");
            jobs.appendCommand(jobId, sequence.incrementAndGet(), treeCommand);
            int treeExit = processRunner.run(jobId, treeCommand, root,
                    line -> { treeOutput.append(line).append('\n'); jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDOUT", line); },
                    line -> jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDERR", line)).join();
            if (treeExit != 0) {
                log.error("Maven dependency:tree failed jobId={} exitCode={} pom={}", jobId, treeExit, pom);
                markFinished(projectId, false);
                updateReportStatus(reportId, ReportStatus.FAILED);
                jobs.finish(jobId, JobStatus.FAILED, treeExit, "MAVEN_TREE_FAILED", "dependency:tree failed");
                return;
            }
            jobs.start(jobId, "DISPLAY_ANCESTORS");
            StringBuilder ancestorsOutput = new StringBuilder();
            List<String> ancestorsCommand = mavenCommand(pom, "dependency:display-ancestors");
            jobs.appendCommand(jobId, sequence.incrementAndGet(), ancestorsCommand);
            int ancestorsExit = processRunner.run(jobId, ancestorsCommand, root,
                    line -> { ancestorsOutput.append(line).append('\n'); jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDOUT", line); },
                    line -> jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDERR", line)).join();
            boolean ancestorsAvailable = ancestorsExit == 0;
            log.info("Maven dependency:display-ancestors finished jobId={} exitCode={} available={}", jobId, ancestorsExit, ancestorsAvailable);
            if (!ancestorsAvailable) {
                jobs.appendOutput(jobId, sequence.incrementAndGet(), "STDERR",
                        "dependency:display-ancestors is unavailable; continuing with dependency:tree and POM parent data");
            }
            List<MavenCoordinate> coordinates = parser.parse(treeOutput.toString());
            log.info("Parsed Maven dependency tree jobId={} coordinates={} ancestorsAvailable={}", jobId, coordinates.size(), ancestorsAvailable);
            replaceGraphSerially(project, coordinates, ancestorsAvailable);
            markFinished(projectId, true);
            updateReportStatus(reportId, ReportStatus.READY);
            jobs.finish(jobId, ancestorsAvailable ? JobStatus.SUCCESS : JobStatus.SUCCESS, 0, null,
                    ancestorsAvailable ? null : "DISPLAY_ANCESTORS_UNAVAILABLE");
        } catch (Exception exception) {
            log.error("Maven dependency analysis failed jobId={} projectId={}", jobId, project.getId(), exception);
            markFinished(projectId, false);
            updateReportStatus(reportId, ReportStatus.FAILED);
            jobs.finish(jobId, JobStatus.FAILED, null, "MAVEN_ANALYSIS_FAILED", exception.getMessage());
        }
    }

    private void replaceGraphSerially(Project project, List<MavenCoordinate> coordinates,
                                      boolean ancestorsAvailable) {
        Object lock = graphReplacementLocks.computeIfAbsent(project.getId(), ignored -> new Object());
        try {
            synchronized (lock) {
                graph.replace(project, coordinates, ancestorsAvailable);
            }
        } finally {
            graphReplacementLocks.remove(project.getId(), lock);
        }
    }

    @Transactional
    void markAnalyzing(UUID projectId) {
        projects.findById(projectId).ifPresent(project -> { project.startMavenAnalysis(); projects.save(project); });
    }

    @Transactional
    void markFinished(UUID projectId, boolean success) {
        projects.findById(projectId).ifPresent(project -> { project.finishMavenAnalysis(success); projects.save(project); });
    }

    private void updateReportStatus(UUID reportId, ReportStatus status) {
        if (reportId == null) return;
        reports.findById(reportId).ifPresent(report -> { report.setStatus(status); reports.save(report); });
    }

    private List<String> mavenCommand(Path pom, String goal) {
        String goalName = goal.contains(":") ? goal.substring(goal.lastIndexOf(':') + 1) : goal;
        return new ArrayList<>(List.of("mvn", "-N", "-f", pom.toString(),
                "org.apache.maven.plugins:maven-dependency-plugin:" + dependencyPluginVersion + ":" + goalName,
                "-DoutputType=text", "-Dverbose"));
    }
}
