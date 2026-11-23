package pl.sca.fix.core.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import pl.sca.fix.core.port.ClockPort;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.VersionLookupRequest;
import pl.sca.fix.core.port.VersionLookupResult;
import pl.sca.fix.core.port.VersionRepositoryClientPort;

/** Persistent, multi-repository cache for Maven version metadata. */
public final class VersionCacheService {
    private static final int SCHEMA_VERSION = 1;
    private final FileSystemPort fileSystem;
    private final VersionRepositoryClientPort client;
    private final ClockPort clock;
    private final Path cacheRoot;
    private final Duration blockedDuration;
    private final pl.sca.fix.core.port.VersionLookupRequest.ProxySettings proxy;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public VersionCacheService(FileSystemPort fileSystem, VersionRepositoryClientPort client,
                               ClockPort clock, Path cacheRoot, Duration blockedDuration) {
        this(fileSystem, client, clock, cacheRoot, blockedDuration, null);
    }

    public VersionCacheService(FileSystemPort fileSystem, VersionRepositoryClientPort client,
                               ClockPort clock, Path cacheRoot, Duration blockedDuration,
                               pl.sca.fix.core.port.VersionLookupRequest.ProxySettings proxy) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot must not be null").toAbsolutePath().normalize();
        this.blockedDuration = positive(blockedDuration, "blockedDuration");
        this.proxy = proxy;
    }

    public VersionCacheResult findVersions(String groupId, String artifactId,
                                           List<MavenRepositorySpec> repositories,
                                           VersionInfoTtl ttl, Duration timeout) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        List<MavenRepositorySpec> configured = unique(repositories);
        Path path = cachePath(groupId, artifactId);
        VersionCacheDocument cached = read(path, groupId, artifactId);
        Instant now = clock.now();
        List<VersionCacheDocument.RepositoryState> states = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean network = false;

        for (MavenRepositorySpec repository : configured) {
            VersionCacheDocument.RepositoryState previous = find(cached, repository.key());
            if (previous != null && previous.blockedUntil() != null && previous.blockedUntil().isAfter(now)) {
                states.add(previous);
                warnings.add(repository.id() + ": REPOSITORY_TEMPORARILY_BLOCKED");
                continue;
            }
            boolean releaseFresh = !repository.releases() || previous != null && previous.releaseExpiresAt() != null
                    && previous.releaseExpiresAt().isAfter(now);
            boolean snapshotFresh = !repository.snapshots() || previous != null && previous.snapshotExpiresAt() != null
                    && previous.snapshotExpiresAt().isAfter(now);
            if (previous != null && releaseFresh && snapshotFresh) {
                states.add(previous);
                continue;
            }

            network = true;
            try {
                VersionLookupResult result = client.findVersions(new VersionLookupRequest(groupId, artifactId,
                        repository.url(), timeout, repository.username(), repository.password(), proxy));
                states.add(success(repository, result.versions(), now, ttl));
            } catch (PortException exception) {
                Instant blockedUntil = now.plus(blockedDuration);
                states.add(failure(repository, previous, blockedUntil, exception));
                warnings.add(repository.id() + ": " + exception.kind());
            }
        }

        List<VersionCacheDocument.VersionEntry> versions = aggregate(states);
        VersionCacheDocument document = new VersionCacheDocument(SCHEMA_VERSION, groupId, artifactId, states);
        if (network || cached == null || !sameStates(cached.repositories(), states)) write(path, document);
        return new VersionCacheResult(versions, warnings, !network && cached != null);
    }

    public VersionCacheResult findVersions(String groupId, String artifactId, EffectivePom effectivePom,
                                           List<MavenRepositorySpec> globalRepositories,
                                           VersionInfoTtl ttl, Duration timeout) {
        List<MavenRepositorySpec> all = new ArrayList<>();
        if (effectivePom != null) all.addAll(effectivePom.repositories());
        if (globalRepositories != null) all.addAll(globalRepositories);
        return findVersions(groupId, artifactId, all, ttl, timeout);
    }

    private VersionCacheDocument.RepositoryState success(MavenRepositorySpec repository, List<String> versions,
                                                         Instant now, VersionInfoTtl ttl) {
        return new VersionCacheDocument.RepositoryState(repository.url(), "AVAILABLE", now,
                repository.releases() ? now.plus(ttl.release()) : null,
                repository.snapshots() ? now.plus(ttl.snapshot()) : null, null,
                versions.stream().distinct().sorted(new MavenVersionComparator()).toList(), null);
    }

    private VersionCacheDocument.RepositoryState failure(MavenRepositorySpec repository,
                                                          VersionCacheDocument.RepositoryState previous,
                                                          Instant blockedUntil, PortException exception) {
        return new VersionCacheDocument.RepositoryState(repository.url(), exception.kind().name(),
                previous == null ? null : previous.fetchedAt(),
                previous == null ? null : previous.releaseExpiresAt(),
                previous == null ? null : previous.snapshotExpiresAt(), blockedUntil,
                previous == null ? List.of() : previous.versions(), exception.kind().name());
    }

    private List<VersionCacheDocument.VersionEntry> aggregate(List<VersionCacheDocument.RepositoryState> states) {
        Map<String, List<String>> sources = new LinkedHashMap<>();
        for (VersionCacheDocument.RepositoryState state : states) {
            for (String version : state.versions()) sources.computeIfAbsent(version, ignored -> new ArrayList<>()).add(state.url());
        }
        return sources.entrySet().stream()
                .map(entry -> new VersionCacheDocument.VersionEntry(entry.getKey(), entry.getValue(),
                        MavenVersionComparator.isSnapshot(entry.getKey()) ? "SNAPSHOT" : "RELEASE"))
                .sorted(Comparator.comparing(VersionCacheDocument.VersionEntry::version, new MavenVersionComparator()).reversed())
                .toList();
    }

    private VersionCacheDocument read(Path path, String groupId, String artifactId) {
        if (!fileSystem.exists(path)) return null;
        try {
            VersionCacheDocument document = mapper.readValue(fileSystem.readBytes(path), VersionCacheDocument.class);
            return document.schemaVersion() == SCHEMA_VERSION && groupId.equals(document.groupId())
                    && artifactId.equals(document.artifactId()) ? document : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void write(Path path, VersionCacheDocument document) {
        try {
            fileSystem.writeAtomically(path, mapper.writeValueAsBytes(document));
        } catch (Exception exception) {
            throw new PortException(PortException.Kind.IO, "Unable to write version cache: " + path, exception);
        }
    }

    private Path cachePath(String groupId, String artifactId) {
        return safe(cacheRoot, groupId).resolve(safeSegment(artifactId)).resolve("versions.json");
    }

    private static Path safe(Path root, String value) { return root.resolve(safeSegment(value)); }
    private static String safeSegment(String value) {
        if (value == null || value.isBlank() || value.equals(".") || value.equals("..")
                || value.contains("/") || value.contains("\\")) throw new IllegalArgumentException("Unsafe cache key");
        return value;
    }

    private static VersionCacheDocument.RepositoryState find(VersionCacheDocument document, String key) {
        if (document == null) return null;
        return document.repositories().stream().filter(item -> item.url().replaceAll("/+$", "")
                .equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    private static boolean sameStates(List<VersionCacheDocument.RepositoryState> left,
                                      List<VersionCacheDocument.RepositoryState> right) {
        return left.equals(right);
    }

    private static List<MavenRepositorySpec> unique(List<MavenRepositorySpec> values) {
        Map<String, MavenRepositorySpec> result = new LinkedHashMap<>();
        List<MavenRepositorySpec> input = values == null ? List.of() : values;
        for (MavenRepositorySpec value : input) {
            if (value != null) result.putIfAbsent(value.key(), value);
        }
        return List.copyOf(result.values());
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
