package pl.sca.fix.cli;

public final class CliConfigException extends RuntimeException {
    private final String code;
    private final String parameter;

    public CliConfigException(String code, String parameter, String message) {
        super(message);
        this.code = code;
        this.parameter = parameter;
    }

    public String code() { return code; }
    public String parameter() { return parameter; }
}
