package pl.sca.fix.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Read-only analysis goal. It is safe to bind to verify. */
@Mojo(name = "scan", defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public final class ScanMojo extends AbstractScaFixMojo {
    @Override public void execute() throws MojoExecutionException {
        if (apply || commit || pushBranch != null)
            throw new MojoExecutionException("READ_ONLY_GOAL: scan cannot apply, commit or push");
        execute(null);
    }
}
