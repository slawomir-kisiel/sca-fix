package pl.sca.fix.repository.application;

import java.util.UUID;

public class RepositoryNotFoundException extends RuntimeException {
    public RepositoryNotFoundException(UUID id) { super("Repository not found: " + id); }
}
