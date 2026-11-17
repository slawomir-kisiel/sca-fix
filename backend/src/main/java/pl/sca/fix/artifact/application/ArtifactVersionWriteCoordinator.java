package pl.sca.fix.artifact.application;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Serializes the check-and-create operation for artifact versions in H2 and PostgreSQL. */
@Service
public class ArtifactVersionWriteCoordinator {
    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T execute(Supplier<T> operation) {
        lock.lock();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { lock.unlock(); }
            });
            return operation.get();
        }
        try { return operation.get(); } finally { lock.unlock(); }
    }
}
