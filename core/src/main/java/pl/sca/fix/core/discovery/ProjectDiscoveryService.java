package pl.sca.fix.core.discovery;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.sca.fix.core.model.ArtifactCoordinate;
import pl.sca.fix.core.model.ProjectModel;
import pl.sca.fix.core.model.ProjectScope;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.GitPort;

/** Discovers deterministic Maven project scope from Git-visible POM files. */
public final class ProjectDiscoveryService {
    private final GitPort git;
    private final MavenPomParser parser;

    public ProjectDiscoveryService(GitPort git, FileSystemPort fileSystem) {
        this.git = git;
        this.parser = new MavenPomParser(fileSystem);
    }

    public ProjectDiscoveryResult discover(ProjectDiscoveryRequest request) {
        Path root = request.repositoryRoot();
        List<Path> pomPaths = visiblePomPaths(root);
        if (pomPaths.isEmpty()) {
            throw new ProjectDiscoveryException("No tracked pom.xml found in repository: " + root);
        }

        Map<Path, PomDescriptor> descriptors = new LinkedHashMap<>();
        for (Path pomPath : pomPaths) {
            descriptors.put(pomPath, parser.parse(root, pomPath));
        }

        Map<Path, ProjectModel> projects = new LinkedHashMap<>();
        Map<Path, ArtifactCoordinate> resolved = new HashMap<>();
        for (Path path : descriptors.keySet()) {
            projects.put(path, toProject(path, descriptors, resolved, new ArrayDeque<>()));
        }

        List<Path> selectedPaths = deduplicatePhysicalPaths(root, selectPaths(request, descriptors, projects));
        List<ProjectModel> selected = selectedPaths.stream()
                .map(projects::get)
                .sorted(Comparator.comparing(ProjectModel::pomPath))
                .toList();
        ensureUniqueCoordinates(selected);
        return new ProjectDiscoveryResult(selected, new ProjectScope(selected, selected), descriptors);
    }

    /**
     * Git can expose two tracked path spellings for one physical file, for
     * example when a repository contains a hard link or a symlink.  Such paths
     * must not produce two logical Maven projects with the same coordinates.
     * The sorted input makes the retained path deterministic.
     */
    private static List<Path> deduplicatePhysicalPaths(Path repositoryRoot, List<Path> paths) {
        List<Path> result = new ArrayList<>();
        for (Path candidate : paths) {
            Path absolute = repositoryRoot.resolve(candidate).normalize();
            boolean duplicate = false;
            for (Path retained : result) {
                Path retainedAbsolute = repositoryRoot.resolve(retained).normalize();
                if (samePhysicalFile(absolute, retainedAbsolute)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static boolean samePhysicalFile(Path left, Path right) {
        if (left.equals(right)) return true;
        try {
            return Files.isSameFile(left, right);
        } catch (IOException | SecurityException ignored) {
            try {
                return Files.exists(left) && Files.exists(right)
                        && left.toRealPath().equals(right.toRealPath());
            } catch (IOException | SecurityException ignoredAgain) {
                return false;
            }
        }
    }

    private List<Path> visiblePomPaths(Path root) {
        Set<Path> unique = new HashSet<>();
        for (Path candidate : git.trackedFiles(root)) {
            Path relative = relativePath(root, candidate);
            if (relative.getFileName() != null && "pom.xml".equals(relative.getFileName().toString())) {
                unique.add(relative);
            }
        }
        return unique.stream().sorted().toList();
    }

    private List<Path> selectPaths(ProjectDiscoveryRequest request,
                                   Map<Path, PomDescriptor> descriptors,
                                   Map<Path, ProjectModel> projects) {
        List<ProjectSelector> selectors = selectors(request.selectors());
        Set<Path> selected = new HashSet<>();
        for (ProjectSelector selector : selectors) {
            List<Path> matches = projects.entrySet().stream()
                    .filter(entry -> selector.matches(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            if (matches.isEmpty()) {
                throw new ProjectDiscoveryException("Project selector did not match any project: " + selector.value());
            }
            selected.addAll(matches);
        }
        if (request.expandModules()) {
            expandModules(selected, descriptors);
        }
        return selected.stream().sorted().toList();
    }

    private void expandModules(Set<Path> selected, Map<Path, PomDescriptor> descriptors) {
        Set<Path> roots = Set.copyOf(selected);
        for (Path root : roots) {
            expandModules(root, selected, descriptors, new HashSet<>());
        }
    }

    private void expandModules(Path current, Set<Path> selected,
                                Map<Path, PomDescriptor> descriptors, Set<Path> active) {
        if (!active.add(current)) {
            throw new ProjectDiscoveryException("Module cycle detected at POM: " + current);
        }
        PomDescriptor descriptor = descriptors.get(current);
        for (String module : descriptor.modules()) {
            Path modulePath = resolveModule(current, module, descriptors.keySet());
            selected.add(modulePath);
            expandModules(modulePath, selected, descriptors, active);
        }
        active.remove(current);
    }

    private static Path resolveModule(Path parentPom, String module, Set<Path> knownPoms) {
        Path declared = parentPom.getParent() == null ? Path.of(module)
                : parentPom.getParent().resolve(module);
        Path candidate = declared.toString().endsWith(".xml") ? declared : declared.resolve("pom.xml");
        candidate = candidate.normalize();
        if (candidate.isAbsolute() || candidate.startsWith("..") || !knownPoms.contains(candidate)) {
            throw new ProjectDiscoveryException("Module " + module + " from " + parentPom
                    + " does not resolve to a tracked pom.xml");
        }
        return candidate;
    }

    private ProjectModel toProject(Path path, Map<Path, PomDescriptor> descriptors,
                                   Map<Path, ArtifactCoordinate> resolved, Deque<Path> visiting) {
        ArtifactCoordinate coordinate = resolveCoordinate(path, descriptors, resolved, visiting);
        PomDescriptor descriptor = descriptors.get(path);
        ArtifactCoordinate parent = resolveParent(path, descriptor, descriptors, resolved, visiting);
        List<Path> modulePaths = descriptor.modules().stream()
                .map(module -> resolveModule(path, module, descriptors.keySet())).toList();
        return new ProjectModel(coordinate, coordinate.artifactId(), path, descriptor.packaging(), parent, modulePaths);
    }

    private ArtifactCoordinate resolveCoordinate(Path path, Map<Path, PomDescriptor> descriptors,
                                                 Map<Path, ArtifactCoordinate> resolved, Deque<Path> visiting) {
        ArtifactCoordinate cached = resolved.get(path);
        if (cached != null) return cached;
        if (!visiting.add(path)) {
            throw new ProjectDiscoveryException("Parent cycle detected at POM: " + path);
        }
        PomDescriptor descriptor = descriptors.get(path);
        ParentDescriptor parent = descriptor.parent();
        String groupId = descriptor.groupId();
        String version = descriptor.version();
        Path parentPath = findLocalParent(parent, descriptors);
        if (parentPath != null && (groupId == null || version == null)) {
            ArtifactCoordinate parentCoordinate = resolveCoordinate(parentPath, descriptors, resolved, visiting);
            if (groupId == null) groupId = parentCoordinate.groupId();
            if (version == null) version = parentCoordinate.version();
        }
        visiting.remove(path);
        if (groupId == null || descriptor.artifactId() == null || version == null) {
            throw new ProjectDiscoveryException("Incomplete Maven coordinates in " + path
                    + " (groupId/artifactId/version required after local parent resolution)");
        }
        ArtifactCoordinate coordinate = new ArtifactCoordinate(groupId, descriptor.artifactId(), version);
        resolved.put(path, coordinate);
        return coordinate;
    }

    private ArtifactCoordinate resolveParent(Path path, PomDescriptor descriptor,
                                             Map<Path, PomDescriptor> descriptors,
                                             Map<Path, ArtifactCoordinate> resolved,
                                             Deque<Path> visiting) {
        if (descriptor.parent() == null) return null;
        Path localParent = findLocalParent(descriptor.parent(), descriptors);
        if (localParent != null) return resolveCoordinate(localParent, descriptors, resolved, visiting);
        ParentDescriptor parent = descriptor.parent();
        if (!parent.complete()) {
            throw new ProjectDiscoveryException("Incomplete external parent coordinates in " + path);
        }
        return new ArtifactCoordinate(parent.groupId(), parent.artifactId(), parent.version());
    }

    private static Path findLocalParent(ParentDescriptor parent, Map<Path, PomDescriptor> descriptors) {
        if (parent == null || parent.groupId() == null || parent.artifactId() == null) return null;
        List<Path> matches = descriptors.entrySet().stream()
                .filter(entry -> parent.groupId().equals(entry.getValue().groupId())
                        && parent.artifactId().equals(entry.getValue().artifactId())
                        && (parent.version() == null || parent.version().equals(entry.getValue().version())))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (matches.size() > 1) {
            throw new ProjectDiscoveryException("Ambiguous local parent " + parent.groupId() + ":"
                    + parent.artifactId() + " in " + matches);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static List<ProjectSelector> selectors(List<String> rawSelectors) {
        List<ProjectSelector> result = new ArrayList<>();
        for (String raw : rawSelectors == null ? List.<String>of() : rawSelectors) {
            if (raw == null) throw new ProjectDiscoveryException("Project selector must not be null");
            for (String value : raw.split(",")) {
                if (!value.isBlank()) result.add(new ProjectSelector(value));
            }
        }
        if (result.isEmpty()) result.add(new ProjectSelector("*"));
        return List.copyOf(result);
    }

    private static Path relativePath(Path root, Path candidate) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : candidate.normalize();
        Path relative = candidate.isAbsolute() ? normalizedRoot.relativize(normalizedCandidate) : normalizedCandidate;
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new ProjectDiscoveryException("Tracked file is outside repository: " + candidate);
        }
        return relative;
    }

    private static void ensureUniqueCoordinates(List<ProjectModel> projects) {
        Map<String, Path> coordinates = new HashMap<>();
        for (ProjectModel project : projects) {
            String key = project.coordinate().groupId() + ":" + project.coordinate().artifactId();
            Path previous = coordinates.putIfAbsent(key, project.pomPath());
            if (previous != null && !previous.equals(project.pomPath())) {
                throw new ProjectDiscoveryException("Ambiguous coordinates " + key + " in "
                        + previous + " and " + project.pomPath());
            }
        }
    }
}
