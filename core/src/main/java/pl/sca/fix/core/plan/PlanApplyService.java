package pl.sca.fix.core.plan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.GitPort;
import pl.sca.fix.core.port.GitStatus;

/** Applies a portable plan sequentially and keeps going after item-level failures. */
public final class PlanApplyService {
    private static final Logger LOG = LoggerFactory.getLogger(PlanApplyService.class);

    private final FileSystemPort files;
    private final GitPort git;
    private final PomEditor editor;
    private final PlanDocumentValidator validator = new PlanDocumentValidator();

    public PlanApplyService(FileSystemPort files, GitPort git) {
        this.files = Objects.requireNonNull(files, "files must not be null");
        this.git = Objects.requireNonNull(git, "git must not be null");
        this.editor = new PomEditor(files);
    }

    public PlanApplyResult apply(PlanApplyRequest request) {
        PlanDocument plan = request.plan();
        validator.validate(plan);
        Path repository = request.repository().toAbsolutePath().normalize();
        LOG.info("Starting plan apply planId={} repository={} items={} existingExecution={} dryRun={}",
                plan.planId(), repository, plan.items().size(), request.existingExecution(), request.dryRun());
        if (!request.existingExecution()) preflight(plan, repository, request.ignoreLocalChanges());
        else ensureRepository(repository);

        List<PlanItem> updated = new ArrayList<>();
        List<PlanApplyItemResult> results = new ArrayList<>();
        List<PlanIssue> errors = new ArrayList<>();
        int planned = 0, applied = 0, failed = 0, skipped = 0;
        StringBuilder diff = new StringBuilder();
        Set<String> disabled = request.disabledStrategies().stream().map(String::toUpperCase).collect(java.util.stream.Collectors.toSet());
        List<PlanSkipRule> skipRules = PlanSkipRule.parseAll(request.skipLibraries());
        for (PlanItem item : plan.items()) {
            if (skipRules.stream().anyMatch(rule -> rule.matches(item))) {
                skipped++;
                updated.add(copy(item, "SKIPPED", "Library excluded by skip rule"));
                results.add(new PlanApplyItemResult(item.itemId(), "SKIPPED", false, "", "Library excluded by skip rule"));
                LOG.info("Skipping plan item itemId={} reason=SKIP_RULE artifact={}:{}:{}", item.itemId(),
                        item.artifact().groupId(), item.artifact().artifactId(), item.currentVersion());
                continue;
            }
            if (disabled.contains(item.strategy().toUpperCase())) {
                skipped++;
                updated.add(copy(item, "SKIPPED", "Strategy disabled: " + item.strategy()));
                results.add(new PlanApplyItemResult(item.itemId(), "SKIPPED", false, "", "Strategy disabled: " + item.strategy()));
                LOG.info("Skipping plan item itemId={} strategy={} reason=DISABLED_STRATEGY", item.itemId(), item.strategy());
                continue;
            }
            if ("APPLIED".equalsIgnoreCase(item.status()) || "SKIPPED".equalsIgnoreCase(item.status())) {
                skipped++;
                updated.add(item);
                results.add(new PlanApplyItemResult(item.itemId(), item.status(), false, "", item.errorMessage()));
                continue;
            }
            planned++;
            LOG.info("Applying plan item itemId={} strategy={} pomPath={}", item.itemId(), item.strategy(), item.project().pomPath());
            try {
                PomChange change = editor.prepare(toEditRequest(item, repository));
                if (!request.dryRun() && change.changed()) {
                    files.writeAtomically(repository.resolve(item.project().pomPath()).normalize(),
                            change.changedContent().getBytes(StandardCharsets.UTF_8));
                }
                String itemDiff = change.diff();
                if (!itemDiff.isBlank()) {
                    diff.append("# ").append(item.itemId()).append(" ").append(item.project().pomPath()).append('\n')
                            .append(itemDiff);
                }
                updated.add(copy(item, "APPLIED", null));
                results.add(new PlanApplyItemResult(item.itemId(), "APPLIED", change.changed(), itemDiff, null));
                applied++;
                LOG.info("Finished plan item itemId={} status=APPLIED changed={}", item.itemId(), change.changed());
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                PlanIssue issue = exception instanceof PomEditException edit
                        ? edit.issue() : new PlanIssue("POM_EDIT_FAILED", "itemId=" + item.itemId() + " message=" + message);
                errors.add(new PlanIssue(issue.reason(), "itemId=" + item.itemId() + " " + issue.message()));
                updated.add(copy(item, "FAILED", message));
                results.add(new PlanApplyItemResult(item.itemId(), "FAILED", false, "", message));
                failed++;
                LOG.error("Plan item failed itemId={} strategy={} message={}", item.itemId(), item.strategy(), message);
            }
        }
        String status = failed == 0 ? "APPLIED" : "FAILED";
        PlanDocument updatedPlan = new PlanDocument(plan.schemaVersion(), plan.planId(), plan.createdAt(),
                plan.repository(), plan.updateLevel(), updated, plan.warnings(), merge(plan.errors(), errors), status);
        PlanApplySummary summary = new PlanApplySummary(plan.planId(), status, planned, applied, failed, skipped,
                request.ignoreLocalChanges(), request.ignoreLocalChanges(), diff.toString(), results, errors);
        LOG.info("Finished plan apply planId={} status={} applied={} failed={} skipped={} pushBlocked={}",
                plan.planId(), status, applied, failed, skipped, request.ignoreLocalChanges());
        return new PlanApplyResult(updatedPlan, summary);
    }

    private void preflight(PlanDocument plan, Path repository, boolean ignoreLocalChanges) {
        GitStatus status = git.status(repository);
        if (status == null || !status.repository()) throw global("REPOSITORY_NOT_FOUND", "Not a Git repository: " + repository);
        if (!Objects.equals(plan.repository().commitSha(), status.commitSha()))
            throw global("BASE_COMMIT_MISMATCH", "Expected commit " + plan.repository().commitSha() + " but found " + status.commitSha());
        if (!ignoreLocalChanges && !status.clean())
            throw global("WORKTREE_DIRTY", "Repository has local changes: " + status.changedFiles());
        Set<Path> checked = new HashSet<>();
        for (PlanItem item : plan.items()) {
            Path pom = resolvePom(repository, item);
            if (checked.add(pom) && !item.pomSha256().equalsIgnoreCase(files.sha256(pom)))
                throw global("POM_HASH_MISMATCH", "itemId=" + item.itemId() + " POM hash does not match: " + pom);
        }
    }

    private void ensureRepository(Path repository) {
        GitStatus status = git.status(repository);
        if (status == null || !status.repository()) throw global("REPOSITORY_NOT_FOUND", "Not a Git repository: " + repository);
    }

    private Path resolvePom(Path repository, PlanItem item) {
        Path relative = Path.of(item.project().pomPath()).normalize();
        Path pom = repository.resolve(relative).normalize();
        if (!pom.startsWith(repository)) throw global("POM_PATH_OUTSIDE_REPOSITORY", "POM path escapes repository: " + relative);
        if (!files.exists(pom)) throw global("POM_NOT_FOUND", "POM not found for itemId=" + item.itemId() + ": " + pom);
        return pom;
    }

    private PomEditRequest toEditRequest(PlanItem item, Path repository) {
        return new PomEditRequest(resolvePom(repository, item), item.strategy(), item.artifact().groupId(),
                item.artifact().artifactId(), item.currentVersion(), item.targetVersion(),
                item.details().get("propertyName"), item.details().get("scope"), item.details().get("type"),
                item.details().get("classifier"), item.details().getOrDefault("operation", "ADD_OR_UPDATE"),
                item.details(), null);
    }

    private static PlanItem copy(PlanItem item, String status, String errorMessage) {
        return new PlanItem(item.itemId(), item.project(), item.artifact(), item.currentVersion(), item.targetVersion(),
                item.strategy(), item.finding(), item.pomSha256(), item.baseCommitSha(), status,
                item.warnings(), item.errors(), item.details(), errorMessage);
    }

    private static List<PlanIssue> merge(List<PlanIssue> left, List<PlanIssue> right) {
        List<PlanIssue> result = new ArrayList<>(left == null ? List.of() : left);
        for (PlanIssue issue : right) if (!result.contains(issue)) result.add(issue);
        return result;
    }

    private static PlanApplyException global(String reason, String message) { return new PlanApplyException(reason, message); }
}
