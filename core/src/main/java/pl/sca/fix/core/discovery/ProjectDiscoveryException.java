package pl.sca.fix.core.discovery;

/** Controlled failure while discovering or selecting Maven projects. */
public class ProjectDiscoveryException extends RuntimeException {
    public ProjectDiscoveryException(String message) {
        super(message);
    }

    public ProjectDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
