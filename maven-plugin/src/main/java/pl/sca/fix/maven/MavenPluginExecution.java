package pl.sca.fix.maven;

import pl.sca.fix.core.workspace.WorkspaceState;

@FunctionalInterface
public interface MavenPluginExecution {
    WorkspaceState execute(MavenPluginConfiguration configuration) throws Exception;
}
