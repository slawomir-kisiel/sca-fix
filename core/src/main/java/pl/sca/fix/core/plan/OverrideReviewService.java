package pl.sca.fix.core.plan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.maven.EffectivePom;
import pl.sca.fix.core.maven.EffectivePomParser;
import pl.sca.fix.core.maven.MavenVersionComparator;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.MavenDependencyTreeRequest;
import pl.sca.fix.core.port.MavenEffectivePomRequest;
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.report.UpdateLevel;

/** Reviews application-owned CVE overrides without changing the checkout permanently. */
public final class OverrideReviewService {
    private static final Logger LOG = LoggerFactory.getLogger(OverrideReviewService.class);
    private static final Pattern DEPENDENCY_MANAGEMENT = Pattern.compile(
            "<dependencyManagement\\b[^>]*>.*?</dependencyManagement>", Pattern.DOTALL);
    private static final Pattern DEPENDENCY = Pattern.compile(
            "<dependency\\b[^>]*>.*?</dependency>", Pattern.DOTALL);
    private static final Pattern GROUP = Pattern.compile("<groupId>\\s*([^<]+?)\\s*</groupId>");
    private static final Pattern ARTIFACT = Pattern.compile("<artifactId>\\s*([^<]+?)\\s*</artifactId>");
    private static final Pattern VERSION = Pattern.compile("<version>\\s*\\$\\{([^}]+)}\\s*</version>");

    private final FileSystemPort files;
    private final MavenPort maven;
    private final EffectivePomParser parser;
    private final Path probeDirectory;

    public OverrideReviewService(FileSystemPort files, MavenPort maven, Path probeDirectory) {
        this.files = Objects.requireNonNull(files, "files must not be null");
        this.maven = Objects.requireNonNull(maven, "maven must not be null");
        this.parser = new EffectivePomParser(files);
        this.probeDirectory = Objects.requireNonNull(probeDirectory, "probeDirectory must not be null");
    }

    public OverrideReviewResult review(List<PlanningProject> projects, Path repository, String commitSha,
                                       UpdateLevel updateLevel) {
        LOG.info("Starting OverrideReview repository={} projects={}", repository,
                projects == null ? 0 : projects.size());
        List<OverrideReviewEntry> entries = new ArrayList<>();
        List<PlanItem> items = new ArrayList<>();
        List<PlanIssue> warnings = new ArrayList<>();
        List<PlanIssue> errors = new ArrayList<>();
        for (PlanningProject project : projects == null ? List.<PlanningProject>of() : projects) {
            if (!project.editable() || !Files.isRegularFile(project.pomPath())) continue;
            for (OverrideDeclaration declaration : declarations(project.pomPath())) {
                OverrideReviewEntry entry;
                try {
                    entry = reviewOne(project, declaration, commitSha);
                } catch (RuntimeException exception) {
                    PlanIssue issue = new PlanIssue("OVERRIDE_REVIEW_FAILED",
                            "projectId=" + project.projectId() + " pomPath=" + project.pomPath()
                                    + " library=" + declaration.groupId() + ":" + declaration.artifactId()
                                    + " message=" + exception.getMessage());
                    entry = declaration.entry(project, OverrideReviewStatus.INCONCLUSIVE, issue, null);
                }
                entries.add(entry);
                if (entry.issue() != null) {
                    if (entry.status() == OverrideReviewStatus.INCONCLUSIVE) errors.add(entry.issue());
                    else warnings.add(entry.issue());
                }
                if (entry.status() == OverrideReviewStatus.REDUNDANT) {
                    String pomPath = repository.toAbsolutePath().normalize()
                            .relativize(project.pomPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
                    String before = entry.resolvedVersionBefore();
                    String after = entry.resolvedVersionAfter() == null ? before : entry.resolvedVersionAfter();
                    items.add(new PlanItem(stableId(pomPath, declaration),
                            new PlanProject(project.effectivePom().groupId(), project.effectivePom().artifactId(),
                                    project.effectivePom().version(), pomPath),
                            new PlanArtifact(declaration.groupId(), declaration.artifactId()), before, after,
                            "REMOVE_CVE_OVERRIDE", null, project.effectivePom().metadata().hash(), commitSha,
                            "PLANNED", List.of(), List.of(), Map.of(
                                    "propertyName", declaration.propertyName(),
                                    "forcedVersion", declaration.forcedVersion(),
                                    "operation", "REMOVE_CVE_OVERRIDE")));
                }
            }
        }
        items.sort(Comparator.comparing(PlanItem::itemId));
        PlanDocument plan = new PlanDocument(1, "plan-" + hash(String.valueOf(commitSha) + items), Instant.now(),
                new PlanRepository(repository.toString(), commitSha), updateLevel == null ? UpdateLevel.MINOR : updateLevel,
                items, List.copyOf(new LinkedHashSet<>(warnings)), List.copyOf(new LinkedHashSet<>(errors)));
        LOG.info("Finished OverrideReview overrides={} redundant={} warnings={} errors={}", entries.size(),
                items.size(), warnings.size(), errors.size());
        return new OverrideReviewResult(entries, plan);
    }

    private OverrideReviewEntry reviewOne(PlanningProject project, OverrideDeclaration declaration,
                                          String commitSha) {
        String original = files.readText(project.pomPath(), StandardCharsets.UTF_8);
        String before = resolvedVersion(project.effectivePom(), declaration);
        Path backup = project.pomPath().resolveSibling(project.pomPath().getFileName() + ".scafix-override-" + UUID.randomUUID());
        // Do not derive Maven output names from projectId. It may contain unresolved
        // Maven expressions such as ${revision}; Maven expands those expressions
        // while creating the file, so the path passed to the caller would no longer
        // point to the generated output. Probe files are internal implementation
        // details and therefore use an opaque, placeholder-free name.
        String probeId = UUID.randomUUID().toString();
        Path output = probeDirectory.resolve("effective-pom-" + probeId + ".xml");
        Path dependencyTreeOutput = probeDirectory.resolve("dependency-tree-" + probeId + ".json");
        boolean moved = false;
        try {
            LOG.info("Starting OverrideReview probe projectId={} library={}:{}", project.projectId(),
                    declaration.groupId(), declaration.artifactId());
            files.createDirectories(probeDirectory);
            files.move(project.pomPath(), backup);
            moved = true;
            files.writeBytes(project.pomPath(), declaration.removeFrom(original).getBytes(StandardCharsets.UTF_8));
            maven.generateEffectivePom(new MavenEffectivePomRequest(project.pomPath(), output));
            maven.generateDependencyTree(new MavenDependencyTreeRequest(project.pomPath(), dependencyTreeOutput,
                    null, false));
            EffectivePom afterPom = parser.parseModel(output,
                    new EffectivePom.Metadata(files.sha256(backup), files.lastModified(backup)), project.effectivePom().pomChain());
            String after = resolvedVersion(afterPom, declaration);
            OverrideReviewStatus status = after == null ? OverrideReviewStatus.REVIEW_REQUIRED
                    : MavenVersionComparator.compareVersions(after, before) >= 0
                    ? OverrideReviewStatus.REDUNDANT : OverrideReviewStatus.KEEP;
            String reason = status == OverrideReviewStatus.REDUNDANT ? "OVERRIDE_REDUNDANT" : "OVERRIDE_REQUIRED";
            PlanIssue issue = new PlanIssue(reason, "projectId=" + project.projectId() + " library="
                    + declaration.groupId() + ":" + declaration.artifactId() + " versionBefore=" + before
                    + " versionAfter=" + after);
            LOG.info("Finished OverrideReview probe projectId={} library={}:{} status={}", project.projectId(),
                    declaration.groupId(), declaration.artifactId(), status);
            return declaration.entry(project, status, issue, after);
        } catch (PortException exception) {
            return declaration.entry(project, OverrideReviewStatus.INCONCLUSIVE,
                    new PlanIssue("MAVEN_ANALYSIS_FAILED", "projectId=" + project.projectId()
                            + " library=" + declaration.groupId() + ":" + declaration.artifactId()
                            + " message=" + exception.getMessage()), null);
        } finally {
            try {
                files.deleteIfExists(project.pomPath());
                if (moved) files.move(backup, project.pomPath());
                files.deleteIfExists(output);
                files.deleteIfExists(dependencyTreeOutput);
            } catch (RuntimeException restoreFailure) {
                throw new PortException(PortException.Kind.IO,
                        "Unable to restore POM after OverrideReview: " + project.pomPath(), restoreFailure);
            }
        }
    }

    private static String resolvedVersion(EffectivePom pom, OverrideDeclaration declaration) {
        return java.util.stream.Stream.concat(pom.dependencies().stream(), pom.dependencyManagement().stream())
                .filter(item -> item.key().equals(declaration.groupId() + ":" + declaration.artifactId()))
                .map(EffectivePom.Dependency::version).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static List<OverrideDeclaration> declarations(Path pom) {
        try {
            String xml = Files.readString(pom, StandardCharsets.UTF_8);
            Matcher management = DEPENDENCY_MANAGEMENT.matcher(xml);
            List<OverrideDeclaration> result = new ArrayList<>();
            while (management.find()) {
                Matcher dependency = DEPENDENCY.matcher(management.group());
                while (dependency.find()) {
                    String block = dependency.group();
                    Matcher group = GROUP.matcher(block), artifact = ARTIFACT.matcher(block), version = VERSION.matcher(block);
                    if (!group.find() || !artifact.find() || !version.find()) continue;
                    String property = version.group(1);
                    String propertyPattern = "<" + Pattern.quote(property) + ">\\s*([^<]+?)\\s*</" + Pattern.quote(property) + ">";
                    Matcher propertyMatcher = Pattern.compile(propertyPattern).matcher(xml);
                    if (propertyMatcher.find()) result.add(new OverrideDeclaration(group.group(1).trim(), artifact.group(1).trim(),
                            property, propertyMatcher.group(1).trim(), block));
                }
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String stableId(String pomPath, OverrideDeclaration declaration) {
        return hash(pomPath + "|" + declaration.groupId() + ":" + declaration.artifactId()
                + "|REMOVE_CVE_OVERRIDE").substring(0, 16);
    }

    private static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record OverrideDeclaration(String groupId, String artifactId, String propertyName,
                                       String forcedVersion, String dependencyBlock) {
        private String removeFrom(String xml) {
            String result = xml.replace(dependencyBlock, "");
            Pattern property = Pattern.compile("<" + Pattern.quote(propertyName) + ">\\s*[^<]+?\\s*</"
                    + Pattern.quote(propertyName) + ">", Pattern.DOTALL);
            return property.matcher(result).replaceFirst("");
        }

        private OverrideReviewEntry entry(PlanningProject project, OverrideReviewStatus status,
                                          PlanIssue issue, String after) {
            return new OverrideReviewEntry(project.projectId(), project.pomPath().toString(), groupId, artifactId,
                    propertyName, forcedVersion, forcedVersion, after, status, issue, List.of(project.projectId()));
        }
    }
}
