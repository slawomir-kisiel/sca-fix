package pl.sca.fix.core.discovery;

/** Controlled failure while parsing one Maven POM. */
public class PomParseException extends RuntimeException {
    public PomParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public PomParseException(String message) {
        super(message);
    }
}
