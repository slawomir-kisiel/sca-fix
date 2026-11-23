package pl.sca.fix.core.port;

/** Runs external commands without exposing ProcessBuilder or Process to core. */
public interface ProcessRunnerPort {
    ProcessResult run(ProcessRequest request) throws PortException;
}
