package pl.sca.fix.core.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.sca.fix.core.discovery.MavenPomParser;
import pl.sca.fix.core.discovery.ParentDescriptor;
import pl.sca.fix.core.discovery.PomDescriptor;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.MavenEffectivePomRequest;
import pl.sca.fix.core.port.MavenPort;

/** Loads a valid effective-POM cache entry or creates it atomically. */
public final class EffectivePomCache {
    private final FileSystemPort fileSystem;
    private final MavenPort maven;
    private final EffectivePomParser parser;
    private final EffectivePomJsonCodec json;
    private final MavenPomParser pomParser;

    public EffectivePomCache(FileSystemPort fileSystem, MavenPort maven) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem must not be null");
        this.maven = Objects.requireNonNull(maven, "maven must not be null");
        this.parser = new EffectivePomParser(fileSystem);
        this.json = new EffectivePomJsonCodec(fileSystem);
        this.pomParser = new MavenPomParser(fileSystem);
    }

    public Loaded load(EffectivePomCacheRequest request) {
        PomDescriptor descriptor = pomParser.parse(request.repositoryRoot(),
                request.repositoryRoot().relativize(request.pomFile()));
        CachePaths paths = descriptor.hasCompleteCoordinates()
                ? paths(request, descriptor) : findCachedPaths(request);
        if (!request.forceRefresh() && paths != null && fileSystem.exists(paths.xml()) && fileSystem.exists(paths.json())) {
            EffectivePom cached = json.read(paths.json());
            if (isFresh(cached, request.repositoryRoot())) return new Loaded(cached, paths, true);
        }

        Path temporaryXml = request.cacheRoot().resolve(".tmp-effective-pom-" + System.nanoTime() + ".xml");
        try {
            maven.generateEffectivePom(new MavenEffectivePomRequest(request.pomFile(), temporaryXml));
            EffectivePomParser.Parsed parsed = parser.parse(temporaryXml);
            List<EffectivePom.PomSource> chain = buildChain(request, descriptor, parsed);
            EffectivePom.Metadata metadata = new EffectivePom.Metadata(
                    fileSystem.sha256(request.pomFile()), fileSystem.lastModified(request.pomFile()));
            EffectivePom model = new EffectivePom(metadata, parsed.groupId(), parsed.artifactId(), parsed.version(),
                    parsed.parent(), parsed.dependencies(), parsed.dependencyManagement(), chain, parsed.repositories());
            CachePaths finalPaths = paths(request, model);
            fileSystem.writeAtomically(finalPaths.xml(), fileSystem.readBytes(temporaryXml));
            json.writeAtomically(finalPaths.json(), model);
            return new Loaded(model, finalPaths, false);
        } finally {
            fileSystem.deleteIfExists(temporaryXml);
        }
    }

    private boolean isFresh(EffectivePom cached, Path repositoryRoot) {
        if (!Integer.valueOf(EffectivePom.CACHE_FORMAT_VERSION).equals(cached.metadata().formatVersion())) return false;
        for (EffectivePom.PomSource source : cached.pomChain()) {
            if (!source.local() || source.pomPath() == null) continue;
            Path pom = repositoryRoot.resolve(source.pomPath()).normalize();
            if (!pom.startsWith(repositoryRoot) || !fileSystem.exists(pom)) return false;
            if (!source.hash().equals(fileSystem.sha256(pom))) return false;
            if (!source.lastModified().equals(fileSystem.lastModified(pom))) return false;
        }
        return true;
    }

    private List<EffectivePom.PomSource> buildChain(EffectivePomCacheRequest request,
                                                     PomDescriptor initial,
                                                     EffectivePomParser.Parsed effective) {
        List<EffectivePom.PomSource> result = new ArrayList<>();
        PomDescriptor current = initial;
        java.util.Set<Path> visited = new java.util.HashSet<>();
        boolean first = true;
        while (current != null && visited.add(current.pomPath())) {
            Path absolute = request.repositoryRoot().resolve(current.pomPath()).normalize();
            String groupId = first && effective.groupId() != null ? effective.groupId() : current.groupId();
            String artifactId = first && effective.artifactId() != null ? effective.artifactId() : current.artifactId();
            String version = first && effective.version() != null ? effective.version() : current.version();
            if (groupId == null || artifactId == null || version == null) break;
            result.add(new EffectivePom.PomSource(groupId, artifactId, version,
                    request.repositoryRoot().relativize(absolute).toString().replace('\\', '/'),
                    fileSystem.sha256(absolute), fileSystem.lastModified(absolute), true));
            first = false;
            ParentDescriptor parent = current.parent();
            if (parent == null || !parent.complete()) break;
            Path relativeParent = absolute.getParent().resolve(parent.relativePath()).normalize();
            if (!relativeParent.startsWith(request.repositoryRoot()) || !fileSystem.exists(relativeParent)) {
                result.add(new EffectivePom.PomSource(parent.groupId(), parent.artifactId(), parent.version(),
                        null, null, null, false));
                break;
            }
            Path nextRelative = request.repositoryRoot().relativize(relativeParent);
            current = pomParser.parse(request.repositoryRoot(), nextRelative);
        }
        return List.copyOf(result);
    }

    private CachePaths findCachedPaths(EffectivePomCacheRequest request) {
        if (!fileSystem.exists(request.cacheRoot()) || !fileSystem.isDirectory(request.cacheRoot())) return null;
        String relativePom = request.repositoryRoot().relativize(request.pomFile()).toString().replace('\\', '/');
        for (Path candidate : fileSystem.listFiles(request.cacheRoot())) {
            if (!candidate.getFileName().toString().equals("effective-pom.json")) continue;
            try {
                EffectivePom model = json.read(candidate);
                if (!model.pomChain().isEmpty() && relativePom.equals(model.pomChain().get(0).pomPath())) {
                    return new CachePaths(candidate.resolveSibling("effective-pom.xml"), candidate);
                }
            } catch (RuntimeException ignored) {
                // A malformed cache entry is simply ignored and regenerated later.
            }
        }
        return null;
    }

    private CachePaths paths(EffectivePomCacheRequest request, PomDescriptor descriptor) {
        if (!descriptor.hasCompleteCoordinates()) {
            throw new EffectivePomParseException("POM must have complete coordinates before cache lookup: " + descriptor.pomPath());
        }
        return paths(request, descriptor.groupId(), descriptor.artifactId(), descriptor.version());
    }

    private CachePaths paths(EffectivePomCacheRequest request, EffectivePom model) {
        return paths(request, model.groupId(), model.artifactId(), model.version());
    }

    private CachePaths paths(EffectivePomCacheRequest request, String groupId, String artifactId, String version) {
        Path directory = safeResolve(request.cacheRoot(), groupId);
        directory = safeResolve(directory, artifactId);
        directory = safeResolve(directory, version);
        return new CachePaths(directory.resolve("effective-pom.xml"), directory.resolve("effective-pom.json"));
    }

    private static Path safeResolve(Path base, String segment) {
        if (segment == null || segment.isBlank() || segment.equals(".") || segment.equals("..")
                || segment.contains("/") || segment.contains("\\")) {
            throw new IllegalArgumentException("Unsafe cache path segment: " + segment);
        }
        return base.resolve(segment);
    }

    public record CachePaths(Path xml, Path json) { }
    public record Loaded(EffectivePom model, CachePaths paths, boolean fromCache) { }
}
