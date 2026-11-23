package pl.sca.fix.core.port;

/** Controlled failure raised by a core infrastructure port. */
public class PortException extends RuntimeException {
    public enum Kind {
        INVALID_INPUT,
        NOT_FOUND,
        IO,
        TIMEOUT,
        AUTHENTICATION,
        PROCESS_FAILED,
        UNSUPPORTED,
        CONFLICT
    }

    private final Kind kind;

    public PortException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public PortException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
