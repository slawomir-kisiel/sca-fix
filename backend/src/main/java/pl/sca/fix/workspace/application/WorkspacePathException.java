package pl.sca.fix.workspace.application;

public class WorkspacePathException extends RuntimeException {
    private final String code;

    public WorkspacePathException(String code, String message) {
        super(message);
        this.code = code;
    }

    public WorkspacePathException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
