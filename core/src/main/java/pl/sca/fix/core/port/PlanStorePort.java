package pl.sca.fix.core.port;

import java.util.Optional;

/** Persists opaque plan documents; plan semantics belong to the plan model.
 * Storage failures and invalid document operations are reported as {@link PortException}. */
public interface PlanStorePort {
    Optional<StoredDocument> load(String planId) throws PortException;

    void save(StoredDocument document) throws PortException;

    boolean exists(String planId) throws PortException;

    void delete(String planId) throws PortException;
}
