package pl.sca.fix.repository.application;

public class RepositoryRegistrationException extends RuntimeException {
    private final String code;

    public RepositoryRegistrationException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
