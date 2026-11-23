package pl.sca.fix.core.plan;

/** Global failure raised before plan modifications begin. */
public final class PlanApplyException extends RuntimeException {
    private final PlanIssue issue;

    public PlanApplyException(String reason, String message) {
        super(message);
        this.issue = new PlanIssue(reason, message);
    }

    public PlanIssue issue() { return issue; }
}
