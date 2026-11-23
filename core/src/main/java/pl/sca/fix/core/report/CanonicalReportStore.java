package pl.sca.fix.core.report;

import java.nio.file.Path;
import java.util.Objects;
import pl.sca.fix.core.port.FileSystemPort;

/** Atomic file store for the canonical workspace report. */
public final class CanonicalReportStore {
    private final FileSystemPort fileSystem;
    private final CanonicalReportJsonCodec codec;

    public CanonicalReportStore(FileSystemPort fileSystem) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem must not be null");
        this.codec = new CanonicalReportJsonCodec();
    }

    public void save(Path path, CanonicalScanReport report) {
        fileSystem.writeAtomically(Objects.requireNonNull(path, "path must not be null"), codec.write(report));
    }

    public CanonicalScanReport load(Path path) {
        return codec.read(fileSystem.readBytes(Objects.requireNonNull(path, "path must not be null")));
    }
}
