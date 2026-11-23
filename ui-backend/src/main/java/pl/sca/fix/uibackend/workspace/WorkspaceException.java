package pl.sca.fix.uibackend.workspace;
public class WorkspaceException extends RuntimeException {
    private final String code;
    public WorkspaceException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}

