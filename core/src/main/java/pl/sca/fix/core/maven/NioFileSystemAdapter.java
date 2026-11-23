package pl.sca.fix.core.maven;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.PortException;

/** Small local filesystem adapter used by the preview tool. */
public final class NioFileSystemAdapter implements FileSystemPort {
    private static final ConcurrentMap<Path, Object> ATOMIC_WRITE_LOCKS = new ConcurrentHashMap<>();
    @Override public boolean exists(Path path) { return Files.exists(path); }
    @Override public boolean isDirectory(Path path) { return Files.isDirectory(path); }
    @Override public byte[] readBytes(Path path) { return io(() -> Files.readAllBytes(path)); }
    @Override public void createDirectories(Path path) { io(() -> Files.createDirectories(path)); }
    @Override public void writeBytes(Path path, byte[] content) { io(() -> Files.write(path, content)); }
    @Override public void move(Path source, Path target) {
        io(() -> Files.move(source, target));
    }
    @Override public void writeAtomically(Path path, byte[] content) {
        Path target = path.toAbsolutePath().normalize();
        Object lock = ATOMIC_WRITE_LOCKS.computeIfAbsent(target, ignored -> new Object());
        try {
            synchronized (lock) {
                writeAtomicallyLocked(target, content);
            }
        } finally {
            ATOMIC_WRITE_LOCKS.remove(target, lock);
        }
    }

    private void writeAtomicallyLocked(Path path, byte[] content) {
        Path temporary = null;
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "." + path.getFileName() + ".", ".tmp");
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
            moveWithRetry(temporary, path);
        } catch (IOException exception) {
            throw new PortException(PortException.Kind.IO, "Unable to write file: " + path, exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { }
            }
        }
    }

    private static void moveWithRetry(Path source, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            try {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException exception) {
                last = exception;
                if (attempt == 11) break;
                try { Thread.sleep(50L * (attempt + 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while replacing " + target, interrupted);
                }
            }
        }
        throw last;
    }
    @Override public void deleteIfExists(Path path) { io(() -> Files.deleteIfExists(path)); }
    @Override public void deleteTree(Path path) { io(() -> { if (Files.isDirectory(path)) Files.walk(path).sorted(java.util.Comparator.reverseOrder()).forEach(this::deleteIfExists); else Files.deleteIfExists(path); }); }
    @Override public Instant lastModified(Path path) { return io(() -> Files.getLastModifiedTime(path).toInstant()); }
    @Override public String sha256(Path path) { return io(() -> java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))); }
    @Override public List<Path> listFiles(Path path) { return io(() -> Files.walk(path).filter(Files::isRegularFile).sorted().toList()); }

    private static <T> T io(IoSupplier<T> supplier) {
        try { return supplier.get(); }
        catch (Exception exception) { throw new PortException(PortException.Kind.IO, "Filesystem operation failed", exception); }
    }
    private static void io(IoRunnable runnable) { io(() -> { runnable.run(); return null; }); }
    @FunctionalInterface private interface IoSupplier<T> { T get() throws Exception; }
    @FunctionalInterface private interface IoRunnable { void run() throws Exception; }
}
