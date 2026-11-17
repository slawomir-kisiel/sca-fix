package pl.sca.fix.report.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.common.process.ExternalProcessRunner;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.job.domain.JobStatus;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;
import pl.sca.fix.report.api.PlanExecutionDtos.ExecutionStartedDto;
import pl.sca.fix.report.domain.PlanItem;
import pl.sca.fix.report.domain.PlanItemStatus;
import pl.sca.fix.report.domain.UpdatePlan;
import pl.sca.fix.report.domain.UpdatePlanStatus;
import pl.sca.fix.report.domain.UpdateStrategy;
import pl.sca.fix.report.persistence.PlanItemRepository;
import pl.sca.fix.report.persistence.UpdatePlanRepository;

@Service
public class PlanExecutionService {
    private static final Logger log = LoggerFactory.getLogger(PlanExecutionService.class);
    private static final Pattern DEPENDENCY = Pattern.compile("(?s)(<dependency\\b[^>]*>.*?<groupId>\\s*%s\\s*</groupId>.*?<artifactId>\\s*%s\\s*</artifactId>.*?<version>\\s*)([^<]+)(\\s*</version>.*?</dependency>)");
    private final UpdatePlanRepository plans;
    private final PlanItemRepository items;
    private final ProjectRepository projects;
    private final RepositoryRepository repositories;
    private final ArtifactTreeNodeRepository nodes;
    private final ArtifactVersionRepository versions;
    private final ArtifactRepository artifacts;
    private final JobService jobs;
    private final ExternalProcessRunner processes;

    public PlanExecutionService(UpdatePlanRepository plans, PlanItemRepository items, ProjectRepository projects,
                                RepositoryRepository repositories, ArtifactTreeNodeRepository nodes,
                                ArtifactVersionRepository versions, ArtifactRepository artifacts,
                                JobService jobs, ExternalProcessRunner processes) {
        this.plans = plans; this.items = items; this.projects = projects; this.repositories = repositories;
        this.nodes = nodes; this.versions = versions; this.artifacts = artifacts; this.jobs = jobs; this.processes = processes;
    }

    @Transactional(readOnly = true)
    public ExecutionStartedDto startApply(UUID planId, boolean diffConfirmed) {
        UpdatePlan plan = plan(planId);
        if (plan.getStatus() != UpdatePlanStatus.PLANNED && plan.getStatus() != UpdatePlanStatus.FAILED)
            throw conflict("Only PLANNED or FAILED plans can be applied");
        if (!diffConfirmed) throw conflict("Diff must be confirmed before Apply");
        if (plan.getRepositoryId() == null) throw conflict("Update plan is not associated with a repository");
        if (items.countByUpdatePlanId(planId) == 0) throw conflict("Plan has no PlanItems");
        Job job = jobs.create("UPDATE_PLAN_APPLY");
        CompletableFuture.runAsync(() -> apply(planId, job.getId()));
        return new ExecutionStartedDto(planId, job.getId(), JobStatus.QUEUED.name());
    }

    @Transactional(readOnly = true)
    public ExecutionStartedDto startCommit(UUID planId) {
        UpdatePlan plan = plan(planId);
        if (plan.getStatus() != UpdatePlanStatus.APPLIED) throw conflict("Only APPLIED plans can be committed");
        Job job = jobs.create("UPDATE_PLAN_COMMIT");
        CompletableFuture.runAsync(() -> commit(planId, job.getId()));
        return new ExecutionStartedDto(planId, job.getId(), JobStatus.QUEUED.name());
    }

    @Transactional(readOnly = true)
    public ExecutionStartedDto startRevert(UUID planId) {
        UpdatePlan plan = plan(planId);
        if (plan.getBranchName() == null || plan.getBaseCommitSha() == null) throw conflict("Plan has no execution branch");
        Job job = jobs.create("UPDATE_PLAN_REVERT");
        CompletableFuture.runAsync(() -> revert(planId, job.getId()));
        return new ExecutionStartedDto(planId, job.getId(), JobStatus.QUEUED.name());
    }

    private void apply(UUID planId, UUID jobId) {
        try {
            UpdatePlan plan = plan(planId);
            Repository repository = repository(plan.getRepositoryId());
            jobs.start(jobId, "VERIFY");
            Path root = Path.of(repository.getClonePath()).toAbsolutePath().normalize();
            String branch = "sca-fix/" + plan.getTaskId().toLowerCase();
            boolean firstExecution = plan.getBranchName() == null || plan.getBaseCommitSha() == null;
            if (firstExecution) {
                requireClean(root, jobId);
                String baseSha = output(root, jobId, List.of("git", "rev-parse", "HEAD"), "GIT_BASE").trim();
                execute(root, jobId, List.of("git", "switch", "-C", branch), "GIT_BRANCH");
                plan.prepareBranch(branch, baseSha); plans.save(plan);
            } else {
                execute(root, jobId, List.of("git", "switch", branch), "GIT_BRANCH");
            }
            int applied = 0;
            int failed = 0;
            List<PlanItem> planItems = items.findByUpdatePlanIdOrderBySelectedAtAsc(planId);
            for (PlanItem item : planItems) {
                if (item.getStatus() == PlanItemStatus.APPLIED) {
                    applied++;
                    continue;
                }
                try {
                    applyItem(item, root, jobId);
                    applied++;
                } catch (RuntimeException exception) {
                    item.markFailed(exception.getMessage()); items.save(item); failed++;
                    jobs.appendOutput(jobId, System.nanoTime(), "STDERR",
                            "PlanItem FAILED " + item.getId() + ": " + exception.getMessage());
                    log.warn("Plan item apply failed planId={} itemId={}", planId, item.getId(), exception);
                }
            }
            jobs.start(jobId, "DIFF");
            execute(root, jobId, List.of("git", "diff", "--"), "GIT_DIFF");
            jobs.appendOutput(jobId, System.nanoTime(), "STDOUT",
                    "Apply summary: applied=" + applied + ", failed=" + failed + ", total=" + planItems.size());
            if (failed == 0) {
                plan.changeStatus(UpdatePlanStatus.APPLIED); plans.save(plan);
                jobs.finish(jobId, JobStatus.SUCCESS, 0, null, null);
            } else {
                plan.changeStatus(UpdatePlanStatus.FAILED); plans.save(plan);
                jobs.finish(jobId, JobStatus.FAILED, null, "PLAN_ITEMS_FAILED",
                        "Apply completed with failures: applied=" + applied + ", failed=" + failed);
            }
        } catch (Exception exception) {
            log.error("Plan apply failed planId={} jobId={}", planId, jobId, exception);
            markPlanFailed(planId);
            finishFailedJob(jobId, "PLAN_APPLY_FAILED", exception);
        }
    }

    private void commit(UUID planId, UUID jobId) {
        try {
            UpdatePlan plan = plan(planId);
            Repository repository = repository(plan.getRepositoryId());
            Path root = Path.of(repository.getClonePath()).toAbsolutePath().normalize();
            jobs.start(jobId, "COMMIT");
            execute(root, jobId, List.of("git", "add", "-A"), "GIT_ADD");
            execute(root, jobId, List.of("git", "commit", "-m", "SCA: " + plan.getTaskId()), "GIT_COMMIT");
            jobs.start(jobId, "VALIDATE");
            for (PlanItem item : items.findByUpdatePlanIdOrderBySelectedAtAsc(planId)) {
                var project = projects.findById(item.getTargetProjectId()).orElseThrow();
                Path pom = root.resolve(project.getPomPath()).normalize();
                int exit = execute(root, jobId, List.of("mvn", "-f", pom.toString(), "validate"), "MAVEN_VALIDATE");
                if (exit != 0) throw new IllegalStateException("Maven validation failed for " + project.getName());
            }
            plan.changeStatus(UpdatePlanStatus.COMMITTED); plans.save(plan);
            jobs.finish(jobId, JobStatus.SUCCESS, 0, null, null);
        } catch (Exception exception) {
            log.error("Plan commit failed planId={} jobId={}", planId, jobId, exception);
            markPlanFailed(planId);
            finishFailedJob(jobId, "PLAN_COMMIT_FAILED", exception);
        }
    }

    private void revert(UUID planId, UUID jobId) {
        try {
            UpdatePlan plan = plan(planId);
            Repository repository = repository(plan.getRepositoryId());
            Path root = Path.of(repository.getClonePath()).toAbsolutePath().normalize();
            jobs.start(jobId, "REVERT");
            execute(root, jobId, List.of("git", "reset", "--hard", plan.getBaseCommitSha()), "GIT_REVERT");
            execute(root, jobId, List.of("git", "clean", "-fd"), "GIT_CLEAN");
            for (PlanItem item : items.findByUpdatePlanIdOrderBySelectedAtAsc(planId)) { item.resetToPlanned(); items.save(item); }
            plan.changeStatus(UpdatePlanStatus.PLANNED); plans.save(plan);
            jobs.finish(jobId, JobStatus.SUCCESS, 0, null, null);
        } catch (Exception exception) {
            log.error("Plan revert failed planId={} jobId={}", planId, jobId, exception);
            finishFailedJob(jobId, "PLAN_REVERT_FAILED", exception);
        }
    }

    private void applyItem(PlanItem item, Path root, UUID jobId) throws IOException {
        var project = projects.findById(item.getTargetProjectId()).orElseThrow(() -> new IllegalStateException("Project not found"));
        var node = nodes.findById(item.getChangePointTreeNodeId()).orElseThrow(() -> new IllegalStateException("Tree node not found"));
        var current = versions.findById(node.getArtifactVersionId()).orElseThrow();
        var target = versions.findById(item.getTargetArtifactVersionId()).orElseThrow();
        var artifact = artifacts.findById(current.getArtifactId()).orElseThrow();
        Path pom = root.resolve(project.getPomPath()).normalize();
        String before = Files.readString(pom, StandardCharsets.UTF_8);
        String after = transform(before, artifact.getGroupId(), artifact.getArtifactId(), current.getVersion(), target.getVersion(), item.getStrategy());
        if (before.equals(after)) throw new IllegalStateException("No applicable change found in " + project.getPomPath());
        Files.writeString(pom, after, StandardCharsets.UTF_8);
        item.markApplied(current.getVersion()); items.save(item);
        jobs.appendOutput(jobId, System.nanoTime(), "STDOUT", "Applied " + project.getPomPath() + " " + current.getVersion() + " -> " + target.getVersion());
    }

    private String transform(String xml, String groupId, String artifactId, String current, String target, UpdateStrategy strategy) {
        if (strategy == UpdateStrategy.DIRECT_DEPENDENCY || strategy == UpdateStrategy.DEPENDENCY_MANAGEMENT) {
            Matcher matcher = Pattern.compile(String.format(DEPENDENCY.pattern(), Pattern.quote(groupId), Pattern.quote(artifactId))).matcher(xml);
            if (!matcher.find()) throw new IllegalStateException("Dependency definition not found for " + groupId + ":" + artifactId);
            String replacement = matcher.group(1) + target + matcher.group(3);
            return xml.substring(0, matcher.start()) + replacement + xml.substring(matcher.end());
        }
        if (strategy == UpdateStrategy.PROPERTY) {
            Pattern property = Pattern.compile("(<[A-Za-z0-9_.-]+>\\s*)" + Pattern.quote(current) + "(\\s*</[A-Za-z0-9_.-]+>)");
            Matcher matcher = property.matcher(xml);
            if (!matcher.find()) throw new IllegalStateException("Version property not found for " + current);
            return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + target + matcher.group(2)));
        }
        if (strategy == UpdateStrategy.CVE_OVERRIDE) {
            if (xml.contains("<artifactId>" + artifactId + "</artifactId>")) throw new IllegalStateException("CVE override already exists for " + artifactId);
            return addCveOverride(xml, groupId, artifactId, target);
        }
        throw new IllegalStateException("Strategy does not modify a POM: " + strategy);
    }

    private String addCveOverride(String xml, String groupId, String artifactId, String target) {
        String newline = xml.contains("\r\n") ? "\r\n" : "\n";
        String propertyName = "cve-override." + artifactId + ".version";
        String dependency = "    <dependency>" + newline
                + "      <groupId>" + groupId + "</groupId>" + newline
                + "      <artifactId>" + artifactId + "</artifactId>" + newline
                + "      <version>${" + propertyName + "}</version>" + newline
                + "    </dependency>";
        String result = insertIntoDependencies(xml, dependency, newline);
        String property = "  <" + propertyName + ">" + target + "</" + propertyName + ">";
        return insertIntoProperties(result, property, newline);
    }

    private String insertIntoDependencies(String xml, String dependency, String newline) {
        Matcher management = Pattern.compile("(?s)(<dependencyManagement\\b[^>]*>)(.*?)(</dependencyManagement\\s*>)").matcher(xml);
        if (management.find()) {
            String body = management.group(2);
            Matcher dependencies = Pattern.compile("(?s)(<dependencies\\b[^>]*>)(.*?)(</dependencies\\s*>)").matcher(body);
            if (dependencies.find()) {
                String updatedBody = body.substring(0, dependencies.start(3)) + newline + dependency + newline + body.substring(dependencies.start(3));
                return xml.substring(0, management.start(2)) + updatedBody + xml.substring(management.end(2));
            }
        }
        String block = "  <dependencyManagement>" + newline + "    <dependencies>" + newline
                + dependency + newline + "    </dependencies>" + newline + "  </dependencyManagement>" + newline;
        return xml.replace("</project>", block + "</project>");
    }

    private String insertIntoProperties(String xml, String property, String newline) {
        String propertyName = property.substring(property.indexOf('<') + 1, property.indexOf('>'));
        if (xml.contains("<" + propertyName + ">")) return xml;
        Matcher properties = Pattern.compile("(?s)(<properties\\b[^>]*>)(.*?)(</properties\\s*>)").matcher(xml);
        if (properties.find()) {
            return xml.substring(0, properties.start(3)) + newline + property + newline + xml.substring(properties.start(3));
        }
        String block = "  <properties>" + newline + property + newline + "  </properties>" + newline;
        return xml.replace("</project>", block + "</project>");
    }

    private void requireClean(Path root, UUID jobId) {
        String status = output(root, jobId, List.of("git", "status", "--porcelain"), "GIT_STATUS");
        if (!status.isBlank()) throw new IllegalStateException("Repository checkout contains uncommitted changes");
    }

    private String output(Path root, UUID jobId, List<String> command, String stage) {
        List<String> lines = new ArrayList<>();
        int exit = execute(root, jobId, command, stage, lines);
        if (exit != 0) throw new IllegalStateException(stage + " failed with exit code " + exit);
        return String.join(System.lineSeparator(), lines);
    }

    private int execute(Path root, UUID jobId, List<String> command, String stage) { return execute(root, jobId, command, stage, new ArrayList<>()); }
    private int execute(Path root, UUID jobId, List<String> command, String stage, List<String> collected) {
        jobs.start(jobId, stage);
        jobs.appendCommand(jobId, command);
        return processes.run(jobId, command, root, line -> { collected.add(line); jobs.appendOutput(jobId, System.nanoTime(), "STDOUT", line); }, line -> jobs.appendOutput(jobId, System.nanoTime(), "STDERR", line)).join();
    }

    @Transactional(readOnly = true) private UpdatePlan plan(UUID id) { return plans.findById(id).orElseThrow(() -> notFound("Update plan not found")); }
    @Transactional(readOnly = true) private Repository repository(UUID id) {
        if (id == null) throw new IllegalStateException("Update plan has no repository");
        return repositories.findById(id).orElseThrow(() -> notFound("Repository not found"));
    }
    @Transactional private void markPlanFailed(UUID id) { plans.findById(id).ifPresent(plan -> { plan.changeStatus(UpdatePlanStatus.FAILED); plans.save(plan); }); }
    private void finishFailedJob(UUID jobId, String errorCode, Exception exception) {
        try {
            jobs.finish(jobId, JobStatus.FAILED, null, errorCode, exception.getMessage());
        } catch (Exception finishException) {
            log.error("Unable to finish failed plan job jobId={} errorCode={}", jobId, errorCode, finishException);
        }
    }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
