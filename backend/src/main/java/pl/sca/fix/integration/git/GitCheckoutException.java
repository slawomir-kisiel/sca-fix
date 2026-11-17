package pl.sca.fix.integration.git;

public class GitCheckoutException extends RuntimeException {
    private final String code;

    public GitCheckoutException(String code, String message) { super(message); this.code = code; }
    public GitCheckoutException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String getCode() { return code; }
}
