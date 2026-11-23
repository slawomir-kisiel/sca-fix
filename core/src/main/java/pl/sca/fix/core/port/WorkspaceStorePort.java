package pl.sca.fix.core.port;

import pl.sca.fix.core.workspace.WorkspaceState;
import pl.sca.fix.core.pipeline.PipelineConfiguration;

/** Persists validated workspace state independently of its storage format.
 * Storage failures and missing required documents are reported as {@link PortException}. */
public interface WorkspaceStorePort {
    WorkspaceState load(String workspaceId) throws PortException;

    void save(WorkspaceState state) throws PortException;

    void saveConfiguration(String workspaceId, PipelineConfiguration configuration) throws PortException;

    PipelineConfiguration loadConfiguration(String workspaceId) throws PortException;

    boolean exists(String workspaceId) throws PortException;

    void delete(String workspaceId) throws PortException;
}
