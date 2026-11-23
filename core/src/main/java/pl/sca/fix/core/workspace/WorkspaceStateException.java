package pl.sca.fix.core.workspace;

/** Controlled failure while validating or reading a workspace state document. */
public class WorkspaceStateException extends RuntimeException {
    public WorkspaceStateException(String message) {
        super(message);
    }

    public WorkspaceStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
