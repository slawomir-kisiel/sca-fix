package pl.sca.fix.core.plan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.maven.EffectivePom;
import pl.sca.fix.core.maven.VersionCacheDocument;
import pl.sca.fix.core.maven.VersionRecommendation;
import pl.sca.fix.core.maven.VersionRecommendationService;
import pl.sca.fix.core.report.CanonicalScanReport;
import pl.sca.fix.core.report.CorrelationEntry;
import pl.sca.fix.core.report.CorrelationResult;
import pl.sca.fix.core.report.CorrelationStatus;
import pl.sca.fix.core.report.DependencyTreeOccurrence;
import pl.sca.fix.core.report.UpdateLevel;
import pl.sca.fix.core.report.VulnerableLibrary;

/** Prepares a deterministic, non-mutating plan from report and correlation data. */
public final class AutomaticPlanningService {
    private static final Logger LOG = LoggerFactory.getLogger(AutomaticPlanningService.class);
    private final VersionRecommendationService recommendations = new VersionRecommendationService();

    public AutomaticPlanningResult plan(CanonicalScanReport report, CorrelationResult correlation,
                                         List<PlanningProject> projects,
                                         Map<String, List<VersionCacheDocument.VersionEntry>> versions,
                                         Path repository, String commitSha, UpdateLevel requestedLevel,
                                         AmbiguousStrategy ambiguousStrategy, boolean overrideReview) {
        return plan(report, correlation, projects, versions, repository, commitSha, requestedLevel,
                requestedLevel, ambiguousStrategy, overrideReview);
    }

    public AutomaticPlanningResult plan(CanonicalScanReport report, CorrelationResult correlation,
                                         List<PlanningProject> projects,
                                         Map<String, List<VersionCacheDocument.VersionEntry>> versions,
                                         Path repository, String commitSha, UpdateLevel requestedLevel,
                                         UpdateLevel cveOverrideUpdateLevel,
                                         AmbiguousStrategy ambiguousStrategy, boolean overrideReview) {
        UpdateLevel level = requestedLevel == null ? UpdateLevel.MINOR : requestedLevel;
        UpdateLevel cveLevel = cveOverrideUpdateLevel == null ? UpdateLevel.PATCH : cveOverrideUpdateLevel;
        AmbiguousStrategy policy = ambiguousStrategy == null ? AmbiguousStrategy.COMMON_SOURCE : ambiguousStrategy;
        List<PlanningProject> projectList = projects == null ? List.of() : projects;
        Map<String, PlanningProject> projectById = projectList.stream()
                .collect(LinkedHashMap::new, (map, value) -> map.put(value.projectId(), value), Map::putAll);
        Map<String, List<VersionCacheDocument.VersionEntry>> versionMap = versions == null ? Map.of() : versions;
        List<VulnerableLibrary> libraries = report == null ? List.of() : report.vulnerableLibraries();
        Map<String, PlanItem> plannedItems = new LinkedHashMap<>();
        List<PlanIssue> issues = new ArrayList<>();
        int occurrences = 0;
        int skipped = 0;
        Map<String, CorrelationEntry> entries = new HashMap<>();
        if (correlation != null) correlation.entries().forEach(entry -> entries.put(entry.libraryKey(), entry));
        LOG.info("Starting automatic planning findings={} libraries={} level={} ambiguousStrategy={}",
                report == null ? 0 : report.cves().size(), libraries.size(), level, policy);

        for (VulnerableLibrary library : libraries) {
            String libraryKey = key(library);
            CorrelationEntry entry = entries.get(key(library));
            if (entry == null) {
                skipped++;
                issues.add(issue("MISSING_CORRELATION", "library=" + libraryKey
                        + " no correlation entry was found"));
                continue;
            }
            List<DependencyTreeOccurrence> selected = select(entry, policy);
            occurrences += entry.occurrences().size();
            if (selected.isEmpty()) {
                skipped++;
                issues.add(reason(entry.status(), libraryKey, entry.occurrences().size(), policy));
                continue;
            }
            Map<String, DependencyTreeOccurrence> unique = new LinkedHashMap<>();
            selected.forEach(occurrence -> unique.putIfAbsent(occurrence.projectId() + "|" + occurrence.pathId(), occurrence));
            for (DependencyTreeOccurrence occurrence : unique.values()) {
                PlanningProject occurrenceProject = projectById.get(occurrence.projectId());
                PlanItem item = createItem(library, occurrence, occurrenceProject, projectList, versionMap,
                                level, cveLevel, commitSha, repository, issues);
                if (item == null) skipped++;
                else {
                    plannedItems.putIfAbsent(item.itemId(), item);
                }
            }
        }
        List<PlanItem> items = new ArrayList<>(plannedItems.values());
        items.sort(Comparator.comparing((PlanItem item) -> item.project().pomPath())
                .thenComparing(item -> item.artifact().groupId())
                .thenComparing(item -> item.artifact().artifactId())
                .thenComparing(PlanItem::currentVersion)
                .thenComparing(PlanItem::targetVersion)
                .thenComparing(PlanItem::strategy));
        List<PlanIssue> uniqueIssues = List.copyOf(new LinkedHashSet<>(issues));
        PlanDocument plan = new PlanDocument(1, planId(items, commitSha), Instant.now(),
                new PlanRepository(repository.toString(), commitSha), level, items, uniqueIssues, List.of());
        PlanningSummary summary = new PlanningSummary(report == null ? 0 : report.cves().size(),
                libraries.size(), occurrences, items.size(), skipped, uniqueIssues);
        LOG.info("Finished automatic planning plannedItems={} skippedItems={} reasons={}",
                items.size(), skipped, issues.size());
        return new AutomaticPlanningResult(plan, summary);
    }

    private PlanItem createItem(VulnerableLibrary library, DependencyTreeOccurrence occurrence,
                                PlanningProject occurrenceProject, List<PlanningProject> projects,
                                Map<String, List<VersionCacheDocument.VersionEntry>> versions,
                                UpdateLevel level, UpdateLevel cveLevel, String commitSha, Path repository,
                                List<PlanIssue> issues) {
        if (occurrenceProject == null) {
            issues.add(issue("SOURCE_PROJECT_NOT_FOUND", "projectId=" + occurrence.projectId()
                    + " library=" + key(library) + " occurrencePath=" + occurrence.pathId()));
            return null;
        }
        if (!occurrenceProject.editable()) {
            issues.add(issue("SOURCE_PROJECT_NOT_EDITABLE", "projectId=" + occurrence.projectId()
                    + " pomPath=" + occurrenceProject.pomPath() + " library=" + key(library)));
            return null;
        }
        Declaration declaration = declaration(library, occurrenceProject, projects);
        PlanningProject changeProject = declaration.project();
        if (!changeProject.editable()) {
            issues.add(issue("SOURCE_PROJECT_NOT_EDITABLE", "projectId=" + changeProject.projectId()
                    + " pomPath=" + changeProject.pomPath() + " library=" + key(library)
                    + " strategy=" + declaration.strategy()));
            return null;
        }
        String versionKey = library.groupId() + ":" + library.artifactId();
        UpdateLevel recommendationLevel = "CVE_OVERRIDE".equals(declaration.strategy()) ? cveLevel : level;
        String currentVersion = currentVersion(library, occurrence, changeProject);
        VersionRecommendation recommendation = recommendations.recommend(library.groupId(), library.artifactId(),
                currentVersion, versions.getOrDefault(versionKey, List.of()), recommendationLevel);
        if (recommendation.recommendedVersion() == null) {
            issues.add(issue("NO_VERSION_RECOMMENDATION", "library=" + versionKey
                    + " currentVersion=" + currentVersion + " updateLevel=" + recommendationLevel
                    + " projectId=" + occurrence.projectId() + " occurrencePath=" + occurrence.pathId()));
            return null;
        }
        String pomPath = repository.relativize(changeProject.pomPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
        String cve = library.cve().isEmpty() ? null : library.cve().get(0);
        return new PlanItem(stableItemId(pomPath, library, declaration.strategy),
                new PlanProject(changeProject.effectivePom().groupId(), changeProject.effectivePom().artifactId(),
                        changeProject.effectivePom().version(), pomPath),
                new PlanArtifact(library.groupId(), library.artifactId()), currentVersion,
                recommendation.recommendedVersion(), declaration.strategy, cve == null ? null
                        : new PlanFinding(cve, "CANONICAL_REPORT", occurrence.pathId()),
                changeProject.effectivePom().metadata().hash(), commitSha, "PLANNED", List.of(), List.of(),
                declaration.details());
    }

    private static String currentVersion(VulnerableLibrary library, DependencyTreeOccurrence occurrence,
                                         PlanningProject project) {
        if (project != null && project.effectivePom() != null) {
            String key = library.groupId() + ":" + library.artifactId();
            String direct = project.effectivePom().dependencies().stream()
                    .filter(dependency -> dependency.key().equals(key))
                    .map(EffectivePom.Dependency::version)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (direct != null) return direct;
            String managed = project.effectivePom().dependencyManagement().stream()
                    .filter(dependency -> dependency.key().equals(key))
                    .map(EffectivePom.Dependency::version)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (managed != null) return managed;
        }
        return occurrence.resolvedVersion();
    }

    private static Declaration declaration(VulnerableLibrary library, PlanningProject project,
                                           List<PlanningProject> projects) {
        EffectivePom pom = project.effectivePom();
        Map<String, PlanningProject> byId = projects.stream().collect(LinkedHashMap::new,
                (map, value) -> map.put(value.projectId(), value), Map::putAll);
        for (EffectivePom.Dependency dependency : pom.dependencies()) {
            if (dependency.key().equals(artifactKey(library)))
                return declaration("DIRECT_DEPENDENCY", dependency, project, byId);
        }
        for (EffectivePom.Dependency dependency : pom.dependencyManagement()) {
            if (dependency.key().equals(artifactKey(library)))
                return declaration("DEPENDENCY_MANAGEMENT", dependency, project, byId);
        }
        // A CVE override is valid even when the vulnerable artifact is transitive.
        return new Declaration("CVE_OVERRIDE", project, Map.of());
    }

    private static Declaration declaration(String defaultStrategy, EffectivePom.Dependency dependency,
                                           PlanningProject fallback, Map<String, PlanningProject> projects) {
        String sourceProjectId = dependency.versionLocation() == null ? fallback.projectId()
                : dependency.versionLocation().artifact();
        PlanningProject owner = projects.get(sourceProjectId);
        if (owner == null) {
            // The effective version is owned by a POM outside the editable manifest.
            // Do not create a DIRECT_DEPENDENCY item for a child without <version>.
            return new Declaration("CVE_OVERRIDE", fallback, Map.of());
        }
        DeclarationSource source = localDeclarationStrategy(owner.pomPath(), dependency.groupId(),
                dependency.artifactId(), defaultStrategy);
        return source == null ? new Declaration("CVE_OVERRIDE", fallback, Map.of())
                : new Declaration(source.strategy(), owner, source.details());
    }

    private static DeclarationSource localDeclarationStrategy(Path pomPath, String groupId, String artifactId,
                                                              String defaultStrategy) {
        try {
            String xml = Files.readString(pomPath);
            java.util.regex.Pattern dependency = java.util.regex.Pattern.compile(
                    "<dependency\\b[^>]*>.*?</dependency>", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher item = dependency.matcher(xml);
            while (item.find()) {
                String block = item.group();
                if (!hasCoordinate(block, groupId, artifactId)) continue;
                String propertyName = propertyName(block);
                if (propertyName != null) return new DeclarationSource("PROPERTY", Map.of("propertyName", propertyName));
                if (elementText(block, "version") != null) return new DeclarationSource("DIRECT_DEPENDENCY", Map.of());
            }
            java.util.regex.Pattern dependencyManagement = java.util.regex.Pattern.compile(
                    "<dependencyManagement\\b[^>]*>.*?</dependencyManagement>", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher management = dependencyManagement.matcher(xml);
            while (management.find()) {
                java.util.regex.Matcher managedItem = dependency.matcher(management.group());
                while (managedItem.find()) {
                    String block = managedItem.group();
                    if (hasCoordinate(block, groupId, artifactId) && elementText(block, "version") != null)
                        return new DeclarationSource("DEPENDENCY_MANAGEMENT", Map.of());
                }
            }
        } catch (Exception ignored) {
            // The caller will use CVE_OVERRIDE rather than guessing a source location.
        }
        return null;
    }

    private static boolean hasCoordinate(String xml, String groupId, String artifactId) {
        return java.util.regex.Pattern.compile("<groupId>\\s*" + java.util.regex.Pattern.quote(groupId)
                + "\\s*</groupId>").matcher(xml).find()
                && java.util.regex.Pattern.compile("<artifactId>\\s*" + java.util.regex.Pattern.quote(artifactId)
                + "\\s*</artifactId>").matcher(xml).find();
    }

    private static String propertyName(String dependencyXml) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "<version>\\s*\\$\\{([^}]+)}\\s*</version>").matcher(dependencyXml);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String elementText(String xml, String element) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "<" + element + ">\\s*([^<]+?)\\s*</" + element + ">").matcher(xml);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static boolean propertyDeclaration(Path pomPath, String groupId, String artifactId) {
        try {
            String xml = Files.readString(pomPath);
            java.util.regex.Pattern dependency = java.util.regex.Pattern.compile(
                    "<dependency\\b[^>]*>.*?</dependency>", java.util.regex.Pattern.DOTALL);
            java.util.regex.Pattern version = java.util.regex.Pattern.compile(
                    "<version>\\s*\\$\\{[^}]+}\\s*</version>");
            java.util.regex.Pattern group = java.util.regex.Pattern.compile(
                    "<groupId>\\s*" + java.util.regex.Pattern.quote(groupId) + "\\s*</groupId>");
            java.util.regex.Pattern artifact = java.util.regex.Pattern.compile(
                    "<artifactId>\\s*" + java.util.regex.Pattern.quote(artifactId) + "\\s*</artifactId>");
            var matcher = dependency.matcher(xml);
            while (matcher.find()) {
                String block = matcher.group();
                if (group.matcher(block).find() && artifact.matcher(block).find()
                        && version.matcher(block).find()) return true;
            }
        } catch (Exception ignored) {
            // A non-readable source cannot provide a property declaration.
        }
        return false;
    }

    private static List<DependencyTreeOccurrence> select(CorrelationEntry entry, AmbiguousStrategy policy) {
        if (entry.status() != CorrelationStatus.AMBIGUOUS) return entry.occurrences();
        if (policy == AmbiguousStrategy.SKIP) return List.of();
        if (policy == AmbiguousStrategy.ALL) return entry.occurrences();
        if (policy == AmbiguousStrategy.COMMON_SOURCE) {
            return entry.occurrences().stream().collect(java.util.stream.Collectors.groupingBy(
                    item -> item.projectId() + "|" + item.previousNodeId(), LinkedHashMap::new, java.util.stream.Collectors.toList()))
                    .values().stream().filter(group -> group.size() == entry.occurrences().size())
                    .findFirst().orElse(List.of());
        }
        if (policy == AmbiguousStrategy.SHALLOWEST || policy == AmbiguousStrategy.PREFER_ROOT
                || policy == AmbiguousStrategy.PREFER_DIRECT) {
            return entry.occurrences().stream().min(Comparator.comparing(DependencyTreeOccurrence::depth)
                    .thenComparing(DependencyTreeOccurrence::projectId)).stream().toList();
        }
        return List.of();
    }

    private static PlanIssue reason(CorrelationStatus status, String libraryKey, int occurrences,
                                    AmbiguousStrategy policy) {
        if (status == CorrelationStatus.ORPHAN)
            return issue("ORPHAN_DEPENDENCY", "library=" + libraryKey
                    + " no matching dependency node was found in the analyzed project trees");
        return issue("AMBIGUOUS_OCCURRENCE", "library=" + libraryKey + " occurrences=" + occurrences
                + " ambiguousStrategy=" + policy
                + " no common editable change source was resolved");
    }

    private static PlanIssue issue(String reason, String message) {
        return new PlanIssue(reason, message);
    }

    private static String key(VulnerableLibrary library) { return library.groupId() + ":" + library.artifactId() + ":" + library.version(); }
    private static String artifactKey(VulnerableLibrary library) { return library.groupId() + ":" + library.artifactId(); }
    private static String key(EffectivePom.Dependency dependency) { return dependency.key(); }
    private static String stableItemId(String pomPath, VulnerableLibrary library, String strategy) {
        return sha256(pomPath + "|" + library.groupId() + ":" + library.artifactId() + "|" + strategy).substring(0, 16);
    }
    private static String planId(List<PlanItem> items, String commitSha) {
        return "plan-" + sha256(String.valueOf(commitSha) + items).substring(0, 16);
    }
    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private record Declaration(String strategy, PlanningProject project, Map<String, String> details) { }
    private record DeclarationSource(String strategy, Map<String, String> details) { }
}
