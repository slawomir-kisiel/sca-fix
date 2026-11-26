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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.sca.fix.core.maven.EffectivePom;
import pl.sca.fix.core.maven.EffectivePomParser;
import pl.sca.fix.core.maven.MavenVersionComparator;
import pl.sca.fix.core.maven.DependencyTreeNode;
import pl.sca.fix.core.maven.VersionCacheDocument;
import pl.sca.fix.core.maven.VersionRecommendation;
import pl.sca.fix.core.maven.VersionRecommendationService;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.MavenDependencyTreeRequest;
import pl.sca.fix.core.port.MavenEffectivePomRequest;
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.report.ProjectTree;

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
    private static final Pattern VERSION_ELEMENT = Pattern.compile("<version>\\s*[^<]+?\\s*</version>");

    private final FileSystemPort files;
    private final MavenPort maven;
    private final EffectivePomParser parser;
    private final VersionRecommendationService recommendations = new VersionRecommendationService();
    private final Path probeDirectory;

    public OverrideReviewService(FileSystemPort files, MavenPort maven, Path probeDirectory) {
        this.files = Objects.requireNonNull(files, "files must not be null");
        this.maven = Objects.requireNonNull(maven, "maven must not be null");
        this.parser = new EffectivePomParser(files);
        this.probeDirectory = Objects.requireNonNull(probeDirectory, "probeDirectory must not be null");
    }

    public OverrideReviewResult review(List<PlanningProject> projects, Path repository, String commitSha,
                                       UpdateLevel updateLevel) {
        return review(projects, List.of(), Map.of(), repository, commitSha, updateLevel);
    }

    /**
     * Reviews overrides using the already analysed dependency trees and version
     * candidates.  All direct dependencies leading to one overridden artifact
     * are selected before the probe is run, so the effective POM is generated
     * once for the combined temporary change.
     */
    public OverrideReviewResult review(List<PlanningProject> projects, List<ProjectTree> trees,
                                       Map<String, List<VersionCacheDocument.VersionEntry>> versions,
                                       Path repository, String commitSha, UpdateLevel updateLevel) {
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
                List<DirectUpdate> updates = directUpdates(project, trees, versions, declaration, updateLevel);
                try {
                    ProbeResult probe = reviewOne(project, declaration, updates, commitSha);
                    entry = probe.entry();
                    updates = probe.updates();
                } catch (RuntimeException exception) {
                    PlanIssue issue = new PlanIssue("OVERRIDE_REVIEW_FAILED",
                            "projectId=" + project.projectId() + " pomPath=" + project.pomPath()
                                    + " library=" + declaration.groupId() + ":" + declaration.artifactId()
                                    + " message=" + exception.getMessage());
                    entry = declaration.entry(project, OverrideReviewStatus.INCONCLUSIVE, issue, null);
                }
                entries.add(entry);
                for (DirectUpdate update : updates) {
                    items.add(updateItem(project, declaration, update, repository, commitSha));
                    warnings.add(new PlanIssue("OVERRIDE_DIRECT_UPDATE_AVAILABLE",
                            "projectId=" + project.projectId() + " directDependency=" + update.key()
                                    + " currentVersion=" + update.currentVersion()
                                    + " recommendedVersion=" + update.targetVersion()
                                    + " override=" + declaration.groupId() + ":" + declaration.artifactId()));
                }
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
        Map<String, PlanItem> uniqueItems = new java.util.LinkedHashMap<>();
        items.forEach(item -> uniqueItems.putIfAbsent(item.itemId(), item));
        items = new ArrayList<>(uniqueItems.values());
        items.sort(Comparator.comparing(PlanItem::itemId));
        PlanDocument plan = new PlanDocument(1, "plan-" + hash(String.valueOf(commitSha) + items), Instant.now(),
                new PlanRepository(repository.toString(), commitSha), updateLevel == null ? UpdateLevel.MINOR : updateLevel,
                items, List.copyOf(new LinkedHashSet<>(warnings)), List.copyOf(new LinkedHashSet<>(errors)));
        LOG.info("Finished OverrideReview overrides={} redundant={} warnings={} errors={}", entries.size(),
                items.size(), warnings.size(), errors.size());
        return new OverrideReviewResult(entries, plan);
    }

    /**
     * Returns only direct dependencies that lead through the analysed trees to
     * an application-owned CVE override.  This set is used to populate the
     * version cache before review; unrelated transitive and managed entries
     * must not trigger repository lookups.
     */
    public Set<String> versionKeysForReview(List<PlanningProject> projects, List<ProjectTree> trees) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (PlanningProject project : projects == null ? List.<PlanningProject>of() : projects) {
            if (!project.editable() || !Files.isRegularFile(project.pomPath())) continue;
            for (OverrideDeclaration declaration : declarations(project.pomPath())) {
                result.addAll(directDependencyKeys(project, trees, declaration));
            }
        }
        return Set.copyOf(result);
    }

    private ProbeResult reviewOne(PlanningProject project, OverrideDeclaration declaration,
                                  List<DirectUpdate> updates, String commitSha) {
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
            String probePom = declaration.removeFrom(original);
            for (DirectUpdate update : updates) probePom = applyUpdate(probePom, update);
            files.writeBytes(project.pomPath(), probePom.getBytes(StandardCharsets.UTF_8));
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
            return new ProbeResult(declaration.entry(project, status, issue, after), updates);
        } catch (PortException exception) {
            return new ProbeResult(declaration.entry(project, OverrideReviewStatus.INCONCLUSIVE,
                    new PlanIssue("MAVEN_ANALYSIS_FAILED", "projectId=" + project.projectId()
                            + " library=" + declaration.groupId() + ":" + declaration.artifactId()
                            + " message=" + exception.getMessage()), null), updates);
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

    private List<DirectUpdate> directUpdates(PlanningProject project, List<ProjectTree> trees,
                                             Map<String, List<VersionCacheDocument.VersionEntry>> versions,
                                             OverrideDeclaration declaration, UpdateLevel updateLevel) {
        ProjectTree tree = (trees == null ? List.<ProjectTree>of() : trees).stream()
                .filter(item -> item.projectId().equals(project.projectId())).findFirst().orElse(null);
        if (tree == null) return List.of();
        Set<String> directKeys = directDependencyKeys(project, trees, declaration);
        List<DirectUpdate> result = new ArrayList<>();
        for (String key : directKeys) {
            EffectivePom.Dependency dependency = project.effectivePom().dependencies().stream()
                    .filter(item -> item.key().equals(key)).findFirst().orElseGet(() ->
                            project.effectivePom().dependencyManagement().stream()
                                    .filter(item -> item.key().equals(key)).findFirst().orElse(null));
            if (dependency == null || dependency.version() == null) continue;
            Source source = source(project.pomPath(), dependency.groupId(), dependency.artifactId());
            if (source == null || source.propertyName() == null && source.literalVersion() == null) continue;
            VersionRecommendation recommendation = recommendations.recommend(dependency.groupId(), dependency.artifactId(),
                    dependency.version(), versions == null ? List.of() : versions.getOrDefault(key, List.of()), updateLevel);
            if (recommendation.recommendedVersion() == null) continue;
            result.add(new DirectUpdate(dependency.groupId(), dependency.artifactId(), dependency.version(),
                    recommendation.recommendedVersion(), source.strategy(), source.propertyName()));
        }
        return result.stream().distinct().sorted(Comparator.comparing(DirectUpdate::key)).toList();
    }

    private static Set<String> directDependencyKeys(PlanningProject project, List<ProjectTree> trees,
                                                    OverrideDeclaration declaration) {
        ProjectTree tree = (trees == null ? List.<ProjectTree>of() : trees).stream()
                .filter(item -> item.projectId().equals(project.projectId())).findFirst().orElse(null);
        if (tree == null || tree.tree().root() == null) return Set.of();
        String target = declaration.groupId() + ":" + declaration.artifactId();
        LinkedHashSet<String> directKeys = new LinkedHashSet<>();
        for (DependencyTreeNode child : tree.tree().root().children()) {
            String directKey = child.coordinate().groupId() + ":" + child.coordinate().artifactId();
            if (!directKey.equals(target)) collectDirectDependencies(child, directKey, target, directKeys);
        }
        return directKeys;
    }

    private static void collectDirectDependencies(DependencyTreeNode node, String directKey, String target,
                                                   LinkedHashSet<String> result) {
        if (node.coordinate().groupId().concat(":").concat(node.coordinate().artifactId()).equals(target)) {
            result.add(directKey);
            return;
        }
        for (DependencyTreeNode child : node.children()) collectDirectDependencies(child, directKey, target, result);
    }

    private static String applyUpdate(String xml, DirectUpdate update) {
        if (update.propertyName() != null) {
            Pattern property = Pattern.compile("(<" + Pattern.quote(update.propertyName()) + ">\\s*)[^<]+?(\\s*</"
                    + Pattern.quote(update.propertyName()) + ">)", Pattern.DOTALL);
            return property.matcher(xml).replaceFirst(Matcher.quoteReplacement("$1" + update.targetVersion() + "$2"));
        }
        Pattern dependency = Pattern.compile("<dependency\\b[^>]*>.*?</dependency>", Pattern.DOTALL);
        Matcher matcher = dependency.matcher(xml);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String block = matcher.group();
            if (!hasCoordinate(block, update.groupId(), update.artifactId())
                    || !VERSION_ELEMENT.matcher(block).find()) continue;
            String replacement = VERSION_ELEMENT.matcher(block).replaceFirst(Matcher.quoteReplacement("<version>"
                    + update.targetVersion() + "</version>"));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            matcher.appendTail(result);
            return result.toString();
        }
        return xml;
    }

    private PlanItem updateItem(PlanningProject project, OverrideDeclaration declaration, DirectUpdate update,
                                Path repository, String commitSha) {
        String pomPath = repository.toAbsolutePath().normalize().relativize(
                project.pomPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
        Map<String, String> details = new java.util.LinkedHashMap<>();
        details.put("operation", "ADD_OR_UPDATE");
        details.put("overrideProperty", declaration.propertyName());
        if (update.propertyName() != null) details.put("propertyName", update.propertyName());
        return new PlanItem(hash(pomPath + "|" + update.key() + "|" + update.targetVersion()).substring(0, 16),
                new PlanProject(project.effectivePom().groupId(), project.effectivePom().artifactId(),
                        project.effectivePom().version(), pomPath),
                new PlanArtifact(update.groupId(), update.artifactId()), update.currentVersion(), update.targetVersion(),
                update.strategy(), null, project.effectivePom().metadata().hash(), commitSha, "PLANNED", List.of(),
                List.of(), details);
    }

    private static Source source(Path pom, String groupId, String artifactId) {
        try {
            String xml = Files.readString(pom, StandardCharsets.UTF_8);
            String withoutManagement = DEPENDENCY_MANAGEMENT.matcher(xml).replaceAll("");
            Source direct = sourceIn(withoutManagement, groupId, artifactId, "DIRECT_DEPENDENCY");
            if (direct != null) return direct;
            Matcher management = DEPENDENCY_MANAGEMENT.matcher(xml);
            while (management.find()) {
                Source managed = sourceIn(management.group(), groupId, artifactId, "DEPENDENCY_MANAGEMENT");
                if (managed != null) return managed;
            }
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static Source sourceIn(String xml, String groupId, String artifactId, String strategy) {
        Matcher dependency = DEPENDENCY.matcher(xml);
        while (dependency.find()) {
            String block = dependency.group();
            if (!hasCoordinate(block, groupId, artifactId)) continue;
            Matcher property = VERSION.matcher(block);
            if (property.find()) {
                String name = property.group(1).trim();
                return name.startsWith("cve-override.") ? null : new Source("PROPERTY", name, null);
            }
            Matcher literal = VERSION_ELEMENT.matcher(block);
            if (literal.find()) {
                String value = literal.group().replaceFirst("^<version>\\s*", "")
                        .replaceFirst("\\s*</version>$", "").trim();
                return value.isBlank() ? null : new Source(strategy, null, value);
            }
        }
        return null;
    }

    private static boolean hasCoordinate(String block, String groupId, String artifactId) {
        return Pattern.compile("<groupId>\\s*" + Pattern.quote(groupId) + "\\s*</groupId>").matcher(block).find()
                && Pattern.compile("<artifactId>\\s*" + Pattern.quote(artifactId) + "\\s*</artifactId>").matcher(block).find();
    }

    private record Source(String strategy, String propertyName, String literalVersion) { }

    private record DirectUpdate(String groupId, String artifactId, String currentVersion, String targetVersion,
                                String strategy, String propertyName) {
        private String key() { return groupId + ":" + artifactId; }
    }

    private record ProbeResult(OverrideReviewEntry entry, List<DirectUpdate> updates) { }

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
                    String groupId = group.group(1).trim();
                    String artifactId = artifact.group(1).trim();
                    String property = version.group(1).trim();
                    if (!property.startsWith("cve-override." + artifactId + ".version")) {
                        continue;
                    }
                    String propertyPattern = "<" + Pattern.quote(property) + ">\\s*([^<]+?)\\s*</" + Pattern.quote(property) + ">";
                    Matcher propertyMatcher = Pattern.compile(propertyPattern).matcher(xml);
                    if (propertyMatcher.find())
                        result.add(new OverrideDeclaration(groupId, artifactId,
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
