package pl.sca.fix.core.port;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PortTestDoubles {
    private PortTestDoubles() {
    }

    public static final class InMemoryDocumentStore implements PlanStorePort {
        private final Map<String, StoredDocument> documents = new ConcurrentHashMap<>();

        @Override
        public Optional<StoredDocument> load(String id) {
            return Optional.ofNullable(documents.get(id)).map(InMemoryDocumentStore::copy);
        }

        @Override
        public void save(StoredDocument document) {
            documents.put(document.id(), copy(document));
        }

        @Override
        public boolean exists(String id) {
            return documents.containsKey(id);
        }

        @Override
        public void delete(String id) {
            documents.remove(id);
        }

        private static StoredDocument copy(StoredDocument document) {
            return new StoredDocument(document.id(), document.content());
        }
    }

    public static final class FixedClock implements ClockPort {
        private final Instant instant;

        public FixedClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public Instant now() {
            return instant;
        }
    }

    public static final class StubProcessRunner implements ProcessRunnerPort {
        private final Deque<ProcessResult> results;
        private ProcessRequest lastRequest;
        private final List<ProcessRequest> requests = new ArrayList<>();

        public StubProcessRunner(ProcessResult result) {
            this(List.of(result));
        }

        public StubProcessRunner(List<ProcessResult> results) {
            this.results = new ArrayDeque<>(List.copyOf(results));
            if (this.results.isEmpty()) throw new IllegalArgumentException("results must not be empty");
        }

        @Override
        public ProcessResult run(ProcessRequest request) {
            lastRequest = request;
            requests.add(request);
            return results.size() == 1 ? results.peekFirst() : results.removeFirst();
        }

        public ProcessRequest lastRequest() {
            return lastRequest;
        }

        public List<ProcessRequest> requests() {
            return List.copyOf(requests);
        }
    }

    public static final class StubGitPort implements GitPort {
        private GitStatus status = new GitStatus(true, true, "abc", List.of());
        private List<Path> trackedFiles = List.of();
        private final List<String> operations = new ArrayList<>();

        public StubGitPort() {
        }

        @Override
        public GitStatus status(Path repository) {
            operations.add("status:" + repository);
            return status;
        }

        @Override
        public List<Path> trackedFiles(Path repository) {
            operations.add("tracked:" + repository);
            return trackedFiles;
        }

        @Override
        public void add(Path repository, List<Path> paths) {
            operations.add("add:" + paths);
        }

        @Override
        public GitBranch createBranch(Path repository, String branchName) {
            operations.add("create:" + branchName);
            return new GitBranch(branchName, "abc");
        }

        @Override
        public GitBranch checkoutBranch(Path repository, String branchName) {
            operations.add("checkout:" + branchName);
            return new GitBranch(branchName, "abc");
        }

        @Override
        public void deleteBranch(Path repository, String branchName) {
            operations.add("delete:" + branchName);
        }

        @Override
        public String commit(Path repository, String message) {
            operations.add("commit:" + message);
            return "abc";
        }

        @Override
        public void push(Path repository, String branchName) {
            operations.add("push:" + branchName);
        }

        void status(GitStatus status) {
            this.status = status;
        }

        public void trackedFiles(List<Path> trackedFiles) {
            this.trackedFiles = List.copyOf(trackedFiles);
        }

        List<String> operations() {
            return List.copyOf(operations);
        }
    }

    public static final class StubMavenPort implements MavenPort {
        private final MavenExecutionResult result;
        private MavenEffectivePomRequest effectivePomRequest;
        private MavenDependencyTreeRequest dependencyTreeRequest;

        StubMavenPort(MavenExecutionResult result) {
            this.result = result;
        }

        @Override
        public MavenExecutionResult generateEffectivePom(MavenEffectivePomRequest request) {
            effectivePomRequest = request;
            return result;
        }

        @Override
        public MavenExecutionResult generateDependencyTree(MavenDependencyTreeRequest request) {
            dependencyTreeRequest = request;
            return result;
        }
    }

    public static final class StubReportSource implements ReportSourcePort {
        private final ReportDocument document;
        private ReportSourceRequest lastRequest;

        StubReportSource(ReportDocument document) {
            this.document = document;
        }

        @Override
        public ReportDocument load(ReportSourceRequest request) {
            lastRequest = request;
            return document;
        }
    }

    public static final class StubVersionRepository implements VersionRepositoryPort {
        private final VersionLookupResult result;
        private VersionLookupRequest lastRequest;

        StubVersionRepository(VersionLookupResult result) {
            this.result = result;
        }

        @Override
        public VersionLookupResult findVersions(VersionLookupRequest request) {
            lastRequest = request;
            return result;
        }
    }

    public static final class TempFileSystem implements FileSystemPort {
        public TempFileSystem() {
        }
        @Override
        public boolean exists(Path path) {
            return Files.exists(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return Files.isDirectory(path);
        }

        @Override
        public byte[] readBytes(Path path) {
            return io(() -> Files.readAllBytes(path));
        }

        @Override
        public void createDirectories(Path directory) {
            io(() -> Files.createDirectories(directory));
        }

        @Override
        public void writeBytes(Path path, byte[] content) {
            io(() -> Files.write(path, content));
        }

        @Override
        public void writeAtomically(Path path, byte[] content) {
            io(() -> {
                Path parent = path.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
                Files.write(temporary, content);
                try {
                    Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            });
        }

        @Override
        public void deleteIfExists(Path path) {
            io(() -> Files.deleteIfExists(path));
        }

        @Override
        public void deleteTree(Path path) {
            io(() -> {
                if (!Files.exists(path)) return null;
                try (var stream = Files.walk(path)) {
                    stream.sorted(java.util.Comparator.reverseOrder()).forEach(this::deleteUnchecked);
                }
                return null;
            });
        }

        private void deleteUnchecked(Path path) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                throw new PortException(PortException.Kind.IO, exception.getMessage(), exception);
            }
        }

        @Override
        public Instant lastModified(Path path) {
            return io(() -> Files.getLastModifiedTime(path).toInstant());
        }

        @Override
        public String sha256(Path path) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(readBytes(path)));
            } catch (NoSuchAlgorithmException exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public List<Path> listFiles(Path directory) {
            return io(() -> {
                try (var stream = Files.walk(directory)) {
                    return stream.filter(Files::isRegularFile).sorted().toList();
                }
            });
        }

        private static <T> T io(IoSupplier<T> supplier) {
            try {
                return supplier.get();
            } catch (IOException exception) {
                throw new PortException(PortException.Kind.IO, exception.getMessage(), exception);
            }
        }

        private interface IoSupplier<T> {
            T get() throws IOException;
        }
    }

    public static final class FailingAtomicFileSystem implements FileSystemPort {
        private final TempFileSystem delegate = new TempFileSystem();

        public FailingAtomicFileSystem() {
        }

        @Override public boolean exists(Path path) { return delegate.exists(path); }
        @Override public boolean isDirectory(Path path) { return delegate.isDirectory(path); }
        @Override public byte[] readBytes(Path path) { return delegate.readBytes(path); }
        @Override public void createDirectories(Path path) { delegate.createDirectories(path); }
        @Override public void writeBytes(Path path, byte[] content) { delegate.writeBytes(path, content); }
        @Override public void writeAtomically(Path path, byte[] content) {
            throw new PortException(PortException.Kind.IO, "simulated atomic write failure");
        }
        @Override public void deleteIfExists(Path path) { delegate.deleteIfExists(path); }
        @Override public void deleteTree(Path path) { delegate.deleteTree(path); }
        @Override public Instant lastModified(Path path) { return delegate.lastModified(path); }
        @Override public String sha256(Path path) { return delegate.sha256(path); }
        @Override public List<Path> listFiles(Path path) { return delegate.listFiles(path); }
    }
}
