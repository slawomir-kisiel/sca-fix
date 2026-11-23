package pl.sca.fix.core.plan;

/** Controlled validation failure raised while preparing a POM change. */
public final class PomEditException extends RuntimeException {
    private final PlanIssue issue;

    public PomEditException(String reason, String message) {
        super(message);
        this.issue = new PlanIssue(reason, message);
    }

    public PlanIssue issue() { return issue; }
}
