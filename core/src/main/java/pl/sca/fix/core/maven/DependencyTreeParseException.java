package pl.sca.fix.core.maven;

/** Controlled failure while reading or validating a dependency tree. */
public class DependencyTreeParseException extends RuntimeException {
    public DependencyTreeParseException(String message) {
        super(message);
    }

    public DependencyTreeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
