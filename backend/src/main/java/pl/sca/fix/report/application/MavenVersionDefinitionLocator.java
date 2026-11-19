package pl.sca.fix.report.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.sca.fix.common.process.ExternalProcessRunner;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;
import pl.sca.fix.repository.domain.Repository;
import pl.sca.fix.repository.persistence.RepositoryRepository;
import pl.sca.fix.report.api.StrategyDtos.VersionDefinitionDto;

/** Locates the editable source of an effective Maven dependency version. */
@Service
public class MavenVersionDefinitionLocator {
    private static final Logger log = LoggerFactory.getLogger(MavenVersionDefinitionLocator.class);
    private static final String HELP_PLUGIN_PREFIX = "org.apache.maven.plugins:maven-help-plugin:";
    private static final String EFFECTIVE_POM_ROOT = "./data/effective-poms";
    private static final Pattern DEPENDENCY = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<%s>(.*?)</%s>", Pattern.DOTALL);
    private static final Pattern ORIGIN = Pattern.compile("<!--\\s*([^,]+), line (\\d+)\\s*-->");

    private final ExternalProcessRunner processes;
    private final RepositoryRepository repositories;
    private final ProjectRepository projects;
    private final String helpPluginVersion;

    public MavenVersionDefinitionLocator(ExternalProcessRunner processes,
                                         RepositoryRepository repositories,
                                         ProjectRepository projects,
                                         @Value("${sca.maven.help-plugin-version:3.5.2}") String helpPluginVersion) {
        this.processes = processes;
        this.repositories = repositories;
        this.projects = projects;
        this.helpPluginVersion = helpPluginVersion;
    }

    public VersionDefinitionDto locate(Project project, String groupId, String artifactId) {
        return resolve(project, groupId, artifactId).definition();
    }

    /**
     * Resolves both the user-visible definition and the registered project which
     * owns that definition.  The project passed to this method is the project
     * from which the dependency was observed; it is not necessarily the project
     * in which Maven defines the version.
     */
    public DefinitionResolution resolve(Project project, String groupId, String artifactId) {
        try {
            Repository repository = repositories.findById(project.getRepositoryId()).orElse(null);
            if (repository == null) return new DefinitionResolution(null, unavailable("Repository not found"));
            Path pom = Path.of(repository.getClonePath()).resolve(project.getPomPath()).normalize();
            if (!Files.isRegularFile(pom)) return new DefinitionResolution(null, unavailable("Project POM not found"));

            RawDefinition raw = rawDefinition(pom, groupId, artifactId);
            EffectivePomFile effectivePom = prepareEffectivePom(project);
            try {
                if (!effectivePom.cached()) {
                    int exit = processes.run(UUID.randomUUID(),
                            List.of("mvn", "-N", "-f", pom.toString(), HELP_PLUGIN_PREFIX + helpPluginVersion + ":effective-pom",
                                    "-Dverbose", "-Doutput=" + effectivePom.output()),
                            Path.of(repository.getClonePath()), ignored -> { }, ignored -> { }).join();
                    if (exit != 0) return new DefinitionResolution(project,
                            raw.toDto(project, repository, "effective-pom failed with exit code " + exit));
                    effectivePom.persist();
                }
                EffectiveDefinition effectiveDefinition = effectiveDefinition(effectivePom.readPath(), groupId, artifactId);
                return merge(project, repository, raw, effectiveDefinition, groupId, artifactId);
            } finally {
                effectivePom.cleanup();
            }
        } catch (Exception exception) {
            log.warn("Unable to locate Maven version source projectId={} artifact={}:{}",
                    project.getId(), groupId, artifactId, exception);
            return new DefinitionResolution(null, unavailable("Unable to inspect effective POM"));
        }
    }

    private EffectivePomFile prepareEffectivePom(Project project) throws IOException {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();
        String version = project.getVersion();
        if (!validPathSegment(groupId) || !validPathSegment(artifactId) || !validPathSegment(version)) {
            return EffectivePomFile.temporary(Files.createTempFile("sca-effective-pom-", ".xml"));
        }
        Path root = Path.of(EFFECTIVE_POM_ROOT).toAbsolutePath().normalize();
        Path directory = root.resolve(groupId).resolve(artifactId).resolve(version).normalize();
        if (!directory.startsWith(root)) {
            return EffectivePomFile.temporary(Files.createTempFile("sca-effective-pom-", ".xml"));
        }
        Files.createDirectories(directory);
        Path target = directory.resolve("effective-pom.xml");
        if (Files.isRegularFile(target)) {
            log.info("Using cached effective POM projectId={} coordinates={}:{}:{} path={}",
                    project.getId(), groupId, artifactId, version, target);
            return EffectivePomFile.cached(target);
        }
        Path temporary = Files.createTempFile(directory, ".effective-pom-", ".tmp");
        return EffectivePomFile.generated(temporary, target, directory);
    }

    private boolean validPathSegment(String value) {
        return value != null && !value.isBlank() && !value.equals(".") && !value.equals("..")
                && !value.contains("/") && !value.contains("\\");
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record EffectivePomFile(Path output, Path target, Path directory, boolean cached) {
        static EffectivePomFile temporary(Path output) { return new EffectivePomFile(output, null, null, false); }
        static EffectivePomFile cached(Path target) { return new EffectivePomFile(target, target, null, true); }
        static EffectivePomFile generated(Path output, Path target, Path directory) {
            return new EffectivePomFile(output, target, directory, false);
        }

        void persist() throws IOException {
            if (target == null) return;
            moveAtomically(output, target);
            try (var files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".xml"))
                        .filter(path -> !path.equals(target))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // A stale cache file must not invalidate a successful generation.
                            }
                        });
            }
        }

        Path readPath() {
            return target == null ? output : target;
        }

        void cleanup() throws IOException {
            if (!cached && output != null) Files.deleteIfExists(output);
        }
    }

    private DefinitionResolution merge(Project project, Repository repository,
                                       RawDefinition raw, EffectiveDefinition effective,
                                       String groupId, String artifactId) throws IOException {
        if (effective != null) {
            Project sourceProject = projects.findByRepositoryIdOrderByPomPath(repository.getId()).stream()
                    .filter(item -> effective.sourceGroupId().equals(item.getGroupId())
                            && effective.sourceArtifactId().equals(item.getArtifactId()))
                    .findFirst().orElse(null);
            RawDefinition sourceRaw = sourceProject == null ? raw : rawDefinition(sourceProject, repository, groupId, artifactId);
            VersionDefinitionDto definition = new VersionDefinitionDto(true,
                    effective.sourceGroupId() + ":" + effective.sourceArtifactId(),
                    sourceProject == null ? null : sourceProject.getPomPath(),
                    sourceRaw.section() == null ? effective.section() : sourceRaw.section(),
                    sourceRaw.propertyName(), effective.sourceLine(),
                    sourceProject == null ? "Definition is inherited from a non-registered POM" : null);
            return new DefinitionResolution(sourceProject, definition);
        }
        if (raw.found()) return new DefinitionResolution(project, raw.toDto(project, repository, null));
        return new DefinitionResolution(null, unavailable("Version definition not found"));
    }

    private RawDefinition rawDefinition(Project project, Repository repository,
                                        String groupId, String artifactId) throws IOException {
        Path pom = Path.of(repository.getClonePath()).resolve(project.getPomPath()).normalize();
        if (!Files.isRegularFile(pom)) return new RawDefinition(false, null, null, null);
        return rawDefinition(pom, groupId, artifactId);
    }

    private RawDefinition rawDefinition(Path pom, String groupId, String artifactId) throws IOException {
        String xml = Files.readString(pom, StandardCharsets.UTF_8);
        Matcher dependencies = DEPENDENCY.matcher(xml);
        while (dependencies.find()) {
            String block = dependencies.group(1);
            if (!groupId.equals(tag(block, "groupId")) || !artifactId.equals(tag(block, "artifactId"))) continue;
            String version = tag(block, "version");
            String propertyName = version != null && version.startsWith("${") && version.endsWith("}")
                    ? version.substring(2, version.length() - 1) : null;
            String section = isInsideDependencyManagement(xml, dependencies.start())
                    ? "dependencyManagement" : "dependencies";
            return new RawDefinition(true, section, propertyName, null);
        }
        return new RawDefinition(false, null, null, null);
    }

    private EffectiveDefinition effectiveDefinition(Path file, String groupId, String artifactId) throws IOException {
        String xml = Files.readString(file, StandardCharsets.UTF_8);
        Matcher dependencies = DEPENDENCY.matcher(xml);
        while (dependencies.find()) {
            String block = dependencies.group(1);
            if (!groupId.equals(tag(block, "groupId")) || !artifactId.equals(tag(block, "artifactId"))) continue;
            String versionLine = lineWithTag(block, "version");
            Matcher origin = ORIGIN.matcher(versionLine == null ? "" : versionLine);
            String coordinate = null;
            Integer line = null;
            if (origin.find()) {
                coordinate = origin.group(1).trim();
                line = Integer.valueOf(origin.group(2));
            }
            if (coordinate == null) return null;
            String[] parts = coordinate.split(":");
            if (parts.length < 2) return null;
            int start = dependencies.start();
            String before = xml.substring(0, start);
            String section = before.lastIndexOf("<dependencyManagement>") > before.lastIndexOf("</dependencyManagement>")
                    ? "dependencyManagement" : "dependencies";
            return new EffectiveDefinition(parts[0], parts[1], section, line);
        }
        return null;
    }

    private String lineWithTag(String block, String name) {
        for (String line : block.split("\\R")) if (line.contains("<" + name + ">")) return line;
        return null;
    }

    private boolean isInsideDependencyManagement(String xml, int position) {
        return xml.lastIndexOf("<dependencyManagement", position) > xml.lastIndexOf("</dependencyManagement>", position);
    }

    private String tag(String xml, String name) {
        Matcher matcher = Pattern.compile(String.format(TAG.pattern(), name, name)).matcher(xml);
        return matcher.find() ? matcher.group(1).replaceAll("<!--.*?-->", "").trim() : null;
    }

    private VersionDefinitionDto unavailable(String message) {
        return new VersionDefinitionDto(false, null, null, null, null, null, message);
    }

    public record DefinitionResolution(Project sourceProject, VersionDefinitionDto definition) { }

    private record RawDefinition(boolean found, String section, String propertyName, Integer sourceLine) {
        VersionDefinitionDto toDto(Project project, Repository repository, String message) {
            return new VersionDefinitionDto(true,
                    project.getGroupId() + ":" + project.getArtifactId(),
                    project.getPomPath(), section, propertyName, sourceLine, message);
        }
    }

    private record EffectiveDefinition(String sourceGroupId, String sourceArtifactId,
                                       String section, Integer sourceLine) { }
}
