package pl.sca.fix.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Explicit mutation goal; never bound implicitly to a lifecycle phase. */
@Mojo(name = "apply", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = false)
public final class ApplyMojo extends AbstractScaFixMojo {
    @Override public void execute() throws MojoExecutionException {
        if (!apply) throw new MojoExecutionException("APPLY_REQUIRES_EXPLICIT_FLAG: set -DscaFix.apply=true");
        execute(null);
    }
}
