package pl.sca.fix.core.port;

/** Maven operations used by the core analysis pipeline.
 * Missing Maven, timeout and non-zero Maven results are reported as {@link PortException}. */
public interface MavenPort {
    MavenExecutionResult generateEffectivePom(MavenEffectivePomRequest request) throws PortException;

    MavenExecutionResult generateDependencyTree(MavenDependencyTreeRequest request) throws PortException;
}
