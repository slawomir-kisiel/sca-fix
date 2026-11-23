package pl.sca.fix.core.port;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** File operations required by core caches, workspaces and reports.
 * Missing paths and I/O failures are reported as {@link PortException}. */
public interface FileSystemPort {
    boolean exists(Path path);

    boolean isDirectory(Path path);

    byte[] readBytes(Path path);

    default String readText(Path path, Charset charset) {
        return new String(readBytes(path), charset);
    }

    void createDirectories(Path directory);

    void writeBytes(Path path, byte[] content);

    /** Moves one exact file to another path. */
    default void move(Path source, Path target) {
        throw new UnsupportedOperationException("move is not supported by this filesystem adapter");
    }

    /** Writes a file through a temporary file and an atomic move when supported. */
    void writeAtomically(Path path, byte[] content);

    void deleteIfExists(Path path);

    /** Deletes the exact file or directory tree identified by the path. */
    void deleteTree(Path path);

    Instant lastModified(Path path);

    String sha256(Path path);

    /** Lists regular files below the given directory in deterministic order. */
    List<Path> listFiles(Path directory);
}
