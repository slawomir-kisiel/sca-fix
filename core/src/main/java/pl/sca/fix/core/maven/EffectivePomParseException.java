package pl.sca.fix.core.maven;

/** Controlled failure raised while parsing a verbose effective POM. */
public final class EffectivePomParseException extends RuntimeException {
    public EffectivePomParseException(String message) { super(message); }
    public EffectivePomParseException(String message, Throwable cause) { super(message, cause); }
}
