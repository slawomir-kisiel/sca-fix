package pl.sca.fix.core.plan;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Structural validation performed before a plan can be consumed by apply. */
public final class PlanDocumentValidator {
    private static final Set<String> PLAN_STATUSES = Set.of("PLANNED", "APPLIED", "FAILED", "CANCELLED");
    private static final Set<String> ITEM_STATUSES = Set.of("PLANNED", "APPLYING", "APPLIED", "FAILED", "SKIPPED");

    public void validate(PlanDocument plan) {
        if (plan == null) fail("Plan is required");
        if (plan.schemaVersion() != 1) fail("Unsupported plan schema version: " + plan.schemaVersion());
        text(plan.planId(), "planId");
        if (plan.createdAt() == null) fail("createdAt is required");
        if (plan.repository() == null) fail("repository is required");
        text(plan.repository().path(), "repository.path");
        text(plan.repository().commitSha(), "repository.commitSha");
        if (plan.updateLevel() == null) fail("updateLevel is required");
        if (plan.status() != null && !PLAN_STATUSES.contains(plan.status().toUpperCase()))
            fail("Unsupported plan status: " + plan.status());
        Set<String> itemIds = new HashSet<>();
        Map<String, String> changes = new HashMap<>();
        for (PlanItem item : plan.items()) {
            if (item == null) fail("items cannot contain null");
            text(item.itemId(), "itemId");
            if (!itemIds.add(item.itemId())) fail("Duplicate itemId: " + item.itemId());
            if (item.project() == null) fail(item.itemId() + ": project is required");
            text(item.project().groupId(), item.itemId() + ".project.groupId");
            text(item.project().artifactId(), item.itemId() + ".project.artifactId");
            text(item.project().pomPath(), item.itemId() + ".project.pomPath");
            validatePomPath(item.project().pomPath(), item.itemId());
            if (item.artifact() == null) fail(item.itemId() + ": artifact is required");
            text(item.artifact().groupId(), item.itemId() + ".artifact.groupId");
            text(item.artifact().artifactId(), item.itemId() + ".artifact.artifactId");
            text(item.currentVersion(), item.itemId() + ".currentVersion");
            text(item.targetVersion(), item.itemId() + ".targetVersion");
            text(item.strategy(), item.itemId() + ".strategy");
            text(item.pomSha256(), item.itemId() + ".pomSha256");
            text(item.baseCommitSha(), item.itemId() + ".baseCommitSha");
            if (item.status() != null && !ITEM_STATUSES.contains(item.status().toUpperCase()))
                fail(item.itemId() + ": unsupported item status: " + item.status());
            String changeKey = item.project().pomPath() + "|" + item.artifact().groupId()
                    + ":" + item.artifact().artifactId();
            String previous = changes.putIfAbsent(changeKey, item.targetVersion());
            if (previous != null && !previous.equals(item.targetVersion()))
                fail(item.itemId() + ": conflicting changes for " + changeKey);
        }
    }

    private static void validatePomPath(String value, String itemId) {
        Path path;
        try { path = Path.of(value); }
        catch (RuntimeException exception) { fail(itemId + ": invalid pomPath"); return; }
        if (path.isAbsolute() || value.startsWith("/") || value.startsWith("\\")
                || value.matches("^[A-Za-z]:.*") || path.normalize().startsWith(".."))
            fail(itemId + ": pomPath must be relative to repository");
    }

    private static void text(String value, String field) {
        if (value == null || value.isBlank()) fail(field + " is required");
    }

    private static void fail(String message) { throw new PlanValidationException(message); }
}
