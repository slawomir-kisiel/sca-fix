package pl.sca.fix.core.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.sca.fix.core.port.ClockPort;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.VersionLookupRequest;
import pl.sca.fix.core.port.VersionLookupResult;
import pl.sca.fix.core.port.VersionRepositoryClientPort;

class VersionCacheServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void parsesIndependentReleaseAndSnapshotTtls() {
        VersionInfoTtl ttl = VersionInfoTtl.parse("R1d,S10m");
        assertEquals(Duration.ofDays(1), ttl.release());
        assertEquals(Duration.ofMinutes(10), ttl.snapshot());
        assertEquals(Duration.ofDays(1), VersionInfoTtl.parse("R1d").release());
        assertEquals(Duration.ofMinutes(10), VersionInfoTtl.parse("R1d").snapshot());
        assertThrows(IllegalArgumentException.class, () -> VersionInfoTtl.parse("R0d,S1m"));
        assertThrows(IllegalArgumentException.class, () -> VersionInfoTtl.parse("R1x"));
    }

    @Test
    void cachesFullDeduplicatedAndSortedVersionList() throws Exception {
        FixedClock clock = new FixedClock(Instant.parse("2026-11-22T12:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        VersionRepositoryClientPort client = request -> {
            calls.incrementAndGet();
            return new VersionLookupResult(List.of("1.10", "1.2", "1.10", "1.3-SNAPSHOT"), request.repositoryUrl());
        };
        VersionCacheService service = service(client, clock, Duration.ofMinutes(10));
        List<MavenRepositorySpec> repositories = List.of(
                new MavenRepositorySpec("central", "https://repo.example/maven", true, true),
                new MavenRepositorySpec("duplicate", "https://repo.example/maven/", true, true));

        VersionCacheResult first = service.findVersions("org.example", "library", repositories,
                VersionInfoTtl.parse("R1d,S10m"), Duration.ofSeconds(2));
        VersionCacheResult second = service.findVersions("org.example", "library", repositories,
                VersionInfoTtl.parse("R1d,S10m"), Duration.ofSeconds(2));

        assertFalse(first.fromCache());
        assertTrue(second.fromCache());
        assertEquals(1, calls.get());
        assertEquals(List.of("1.10", "1.3-SNAPSHOT", "1.2"),
                first.versions().stream().map(VersionCacheDocument.VersionEntry::version).toList());
        Path cacheFile = temporaryDirectory.resolve("org.example/library/versions.json");
        assertTrue(Files.exists(cacheFile));
        String persisted = Files.readString(cacheFile);
        assertFalse(persisted.contains("repositoryUrls"));
        assertFalse(persisted.contains("\"kind\""));
    }

    @Test
    void blocksFailedRepositoryAndDoesNotBlock404() {
        FixedClock clock = new FixedClock(Instant.parse("2026-11-22T12:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        VersionRepositoryClientPort client = request -> {
            calls.incrementAndGet();
            if (request.repositoryUrl().contains("auth")) throw new PortException(PortException.Kind.AUTHENTICATION, "auth failed");
            return new VersionLookupResult(List.of(), request.repositoryUrl());
        };
        VersionCacheService service = service(client, clock, Duration.ofMinutes(10));
        List<MavenRepositorySpec> repositories = List.of(
                new MavenRepositorySpec("auth", "https://auth.example/maven", true, false),
                new MavenRepositorySpec("empty", "https://empty.example/maven", true, false));

        VersionCacheResult first = service.findVersions("org.example", "library", repositories,
                VersionInfoTtl.defaults(), Duration.ofSeconds(1));
        VersionCacheResult second = service.findVersions("org.example", "library", repositories,
                VersionInfoTtl.defaults(), Duration.ofSeconds(1));

        assertEquals(2, calls.get());
        assertTrue(first.warnings().stream().anyMatch(value -> value.contains("AUTHENTICATION")));
        assertTrue(second.warnings().stream().anyMatch(value -> value.contains("REPOSITORY_TEMPORARILY_BLOCKED")));
    }

    @Test
    void releasesCacheWhenTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-22T12:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        VersionRepositoryClientPort client = request -> {
            calls.incrementAndGet();
            return new VersionLookupResult(List.of("1." + calls.get()), request.repositoryUrl());
        };
        VersionCacheService service = service(client, clock, Duration.ofMinutes(10));
        List<MavenRepositorySpec> repositories = List.of(new MavenRepositorySpec("central", "https://repo.example", true, false));
        service.findVersions("org.example", "library", repositories, VersionInfoTtl.parse("R1m,S10m"), Duration.ofSeconds(1));
        clock.advance(Duration.ofMinutes(2));
        VersionCacheResult refreshed = service.findVersions("org.example", "library", repositories,
                VersionInfoTtl.parse("R1m,S10m"), Duration.ofSeconds(1));
        assertFalse(refreshed.fromCache());
        assertEquals(2, calls.get());
    }

    private VersionCacheService service(VersionRepositoryClientPort client, ClockPort clock, Duration blocked) {
        return new VersionCacheService(new pl.sca.fix.core.maven.NioFileSystemAdapter(), client, clock,
                temporaryDirectory, blocked);
    }

    private static class FixedClock implements ClockPort {
        private final Instant value;
        FixedClock(Instant value) { this.value = value; }
        @Override public Instant now() { return value; }
    }

    private static final class MutableClock implements ClockPort {
        private Instant value;
        MutableClock(Instant value) { this.value = value; }
        @Override public Instant now() { return value; }
        void advance(Duration duration) { value = value.plus(duration); }
    }
}
