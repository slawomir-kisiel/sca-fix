package pl.sca.fix.project.application;

public class ProjectDiscoveryException extends RuntimeException {
    private final String code;

    public ProjectDiscoveryException(String code, String message) { super(message); this.code = code; }
    public ProjectDiscoveryException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String getCode() { return code; }
}
