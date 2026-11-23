package pl.sca.fix.core.plan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pl.sca.fix.core.port.FileSystemPort;

/**
 * Prepares local Maven POM edits while preserving the original text.
 * Maven, Git and network access are intentionally outside this class.
 */
public final class PomEditor {
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9_.:-]*)(?:\\s[^>]*)?>|<(/?)([A-Za-z][A-Za-z0-9_.:-]*)\\s*/>");
    private static final Pattern MODEL_VERSION = Pattern.compile("(?m)^([ \\t]*)<modelVersion\\s*>\\s*4\\.0\\.0\\s*</modelVersion>");
    private static final Pattern XML_DECLARATION = Pattern.compile("<\\?xml[^>]*\\?>\\s*");

    private final FileSystemPort files;

    public PomEditor(FileSystemPort files) { this.files = Objects.requireNonNull(files, "files must not be null"); }

    public PomChange prepare(PomEditRequest request) {
        String original = read(request);
        String originalHash = sha256(original);
        if (request.expectedSha256() != null && !request.expectedSha256().isBlank()
                && !request.expectedSha256().equalsIgnoreCase(originalHash)) {
            throw failure("POM_HASH_MISMATCH", "Expected POM hash " + request.expectedSha256()
                    + " but found " + originalHash + " for " + request.pomPath());
        }
        Indent indent = indentation(original);
        String changed = switch (request.strategy().trim().toUpperCase()) {
            case "DIRECT_DEPENDENCY" -> replaceDependencyVersion(original, request, false);
            case "DEPENDENCY_MANAGEMENT" -> replaceDependencyVersion(original, request, true);
            case "PROPERTY" -> replaceProperty(original, request);
            case "CVE_OVERRIDE" -> upsertOverride(original, request, indent);
            case "REMOVE_CVE_OVERRIDE" -> removeOverride(original, request);
            default -> throw failure("UNSUPPORTED_STRATEGY", "Unsupported POM strategy: " + request.strategy());
        };
        boolean changedFlag = !original.equals(changed);
        return new PomChange(request.pomPath().toString(), request.strategy(), changedFlag,
                originalHash, sha256(changed), indent.token(), original, changed,
                diff(original, changed), List.of(), List.of());
    }

    private String read(PomEditRequest request) {
        try {
            if (!files.exists(request.pomPath())) throw failure("POM_NOT_FOUND", "POM not found: " + request.pomPath());
            String xml = files.readText(request.pomPath(), StandardCharsets.UTF_8);
            if (!xml.contains("<project") || !xml.contains("</project>"))
                throw failure("XML_INVALID", "POM does not contain a complete project element: " + request.pomPath());
            return xml;
        } catch (PomEditException exception) { throw exception; }
        catch (RuntimeException exception) { throw failure("POM_NOT_READABLE", "Unable to read POM: " + request.pomPath()); }
    }

    private String replaceDependencyVersion(String xml, PomEditRequest request, boolean management) {
        ElementRange managementSection = section(xml, "dependencyManagement", 0);
        ElementRange section = management
                ? managementSection == null ? null : child(xml, managementSection, "dependencies")
                : all(xml, "dependencies").stream()
                    .filter(item -> managementSection == null || !inside(item, managementSection))
                    .findFirst().orElse(null);
        if (section == null) throw failure(management ? "DEPENDENCY_MANAGEMENT_NOT_FOUND" : "DEPENDENCY_NOT_FOUND",
                "Required section not found in " + request.pomPath());
        List<ElementRange> dependencies = children(xml, section, "dependency");
        List<ElementRange> matches = dependencies.stream().filter(item -> matchesDependency(xml, item, request)).toList();
        if (matches.isEmpty()) throw failure("DEPENDENCY_NOT_FOUND", "Dependency not found: " + coordinate(request));
        if (matches.size() > 1) throw failure("AMBIGUOUS_DECLARATION", "More than one dependency matches: " + coordinate(request));
        ElementRange match = matches.get(0);
        ElementRange version = child(xml, match, "version");
        if (version == null) throw failure("DEPENDENCY_VERSION_NOT_FOUND", "Dependency has no local version: " + coordinate(request));
        String current = text(xml, version);
        if (request.currentVersion() != null && !request.currentVersion().isBlank()
                && !request.currentVersion().equals(current)) {
            if (request.targetVersion() != null && request.targetVersion().equals(current)) return xml;
            throw failure("CURRENT_VERSION_MISMATCH", "Expected version " + request.currentVersion()
                    + " but found " + current + " for " + coordinate(request));
        }
        return replaceElementText(xml, version, request.targetVersion());
    }

    private String replaceProperty(String xml, PomEditRequest request) {
        if (request.propertyName() == null || request.propertyName().isBlank())
            throw failure("PROPERTY_NOT_FOUND", "Property name is required for PROPERTY strategy");
        List<ElementRange> properties = all(xml, request.propertyName());
        if (properties.isEmpty()) throw failure("PROPERTY_NOT_FOUND", "Property not found: " + request.propertyName());
        if (properties.size() > 1) throw failure("AMBIGUOUS_DECLARATION", "More than one property matches: " + request.propertyName());
        ElementRange property = properties.get(0);
        String current = text(xml, property);
        if (request.currentVersion() != null && !request.currentVersion().isBlank()
                && !request.currentVersion().equals(current))
            if (request.targetVersion() != null && request.targetVersion().equals(current)) return xml;
            else
            throw failure("PROPERTY_VALUE_MISMATCH", "Expected property value " + request.currentVersion()
                    + " but found " + current + " for " + request.propertyName());
        return replaceElementText(xml, property, request.targetVersion());
    }

    private String upsertOverride(String xml, PomEditRequest request, Indent indent) {
        String propertyName = "cve-override." + request.artifactId() + ".version";
        String propertyValue = request.targetVersion();
        if (propertyValue == null || propertyValue.isBlank()) throw failure("TARGET_VERSION_REQUIRED", "Target version is required");
        ElementRange management = section(xml, "dependencyManagement", 0);
        String result = xml;
        if (management == null) {
            String block = indent.line() + "<dependencyManagement>" + indent.nl()
                    + indent.line(2) + "<dependencies>" + indent.nl()
                    + dependencyXml(request, propertyName, indent, 3) + indent.line(2) + "</dependencies>" + indent.nl()
                    + indent.line() + "</dependencyManagement>" + indent.nl();
            result = insertBeforeProjectClose(result, block);
        } else {
            ElementRange dependencies = child(result, management, "dependencies");
            if (dependencies == null) {
                String block = indent.line(1) + "<dependencies>" + indent.nl()
                        + dependencyXml(request, propertyName, indent, 3)
                        + indent.line(2) + "</dependencies>" + indent.nl();
                result = insertAt(result, insertionPoint(result, management.closeStart()), block);
            } else {
                String dependencyXml = result;
                ElementRange existing = children(result, dependencies, "dependency").stream()
                        .filter(item -> matchesDependency(dependencyXml, item, request)).findFirst().orElse(null);
                if (existing != null) {
                    ElementRange version = child(result, existing, "version");
                    if (version == null) throw failure("DEPENDENCY_VERSION_NOT_FOUND", "Override dependency has no version");
                    String current = text(result, version);
                    if (!current.equals("${" + propertyName + "}"))
                        result = replaceElementText(result, version, "${" + propertyName + "}");
                } else {
                    String block = dependencyXml(request, propertyName, indent, 3);
                    result = insertAt(result, insertionPoint(result, dependencies.closeStart()), block);
                }
            }
        }
        ElementRange properties = section(result, "properties", 0);
        String propertyXml = indent.line(2) + "<" + propertyName + ">" + propertyValue + "</" + propertyName + ">" + indent.nl();
        if (properties == null) {
            result = insertBeforeProjectClose(result, indent.line() + "<properties>" + indent.nl()
                    + indent.line(2) + "<" + propertyName + ">" + propertyValue + "</" + propertyName + ">" + indent.nl()
                    + indent.line() + "</properties>" + indent.nl());
        } else {
            ElementRange existing = child(result, properties, propertyName);
            if (existing != null) result = replaceElementText(result, existing, propertyValue);
            else result = insertAt(result, insertionPoint(result, properties.closeStart()), propertyXml);
        }
        return result;
    }

    private String removeOverride(String xml, PomEditRequest request) {
        String propertyName = request.propertyName();
        if (propertyName == null || propertyName.isBlank()) propertyName = "cve-override." + request.artifactId() + ".version";
        ElementRange management = section(xml, "dependencyManagement", 0);
        if (management == null) throw failure("OVERRIDE_NOT_OWNED", "dependencyManagement not found for " + coordinate(request));
        ElementRange dependencies = child(xml, management, "dependencies");
        if (dependencies == null) throw failure("OVERRIDE_NOT_OWNED", "dependencyManagement dependencies not found");
        ElementRange dependency = children(xml, dependencies, "dependency").stream()
                .filter(item -> matchesDependency(xml, item, request)).findFirst().orElse(null);
        if (dependency == null) throw failure("OVERRIDE_NOT_OWNED", "CVE override dependency not found: " + coordinate(request));
        ElementRange version = child(xml, dependency, "version");
        if (version == null || !text(xml, version).equals("${" + propertyName + "}"))
            throw failure("OVERRIDE_NOT_OWNED", "Dependency is not owned by CVE override: " + coordinate(request));
        String result = removeRange(xml, dependency);
        List<ElementRange> usages = all(result, propertyName);
        if (!usages.isEmpty() && !result.contains("${" + propertyName + "}")) {
            ElementRange property = usages.get(0);
            result = removeRange(result, property);
        }
        return result;
    }

    private String dependencyXml(PomEditRequest request, String propertyName, Indent indent, int level) {
        String i = indent.line(level), child = indent.line(level + 1);
        return i + "<dependency>" + indent.nl()
                + child + "<groupId>" + request.groupId() + "</groupId>" + indent.nl()
                + child + "<artifactId>" + request.artifactId() + "</artifactId>" + indent.nl()
                + child + "<version>${" + propertyName + "}</version>" + indent.nl()
                + i + "</dependency>" + indent.nl();
    }

    private boolean matchesDependency(String xml, ElementRange range, PomEditRequest request) {
        if (!request.groupId().equals(elementText(xml, range, "groupId"))
                || !request.artifactId().equals(elementText(xml, range, "artifactId"))) return false;
        return optionalEquals(request.scope(), elementText(xml, range, "scope"))
                && optionalEquals(request.type(), elementText(xml, range, "type"))
                && optionalEquals(request.classifier(), elementText(xml, range, "classifier"));
    }

    private static boolean optionalEquals(String expected, String actual) {
        return expected == null || expected.isBlank() || Objects.equals(expected, actual);
    }

    private String elementText(String xml, ElementRange parent, String name) {
        ElementRange child = child(xml, parent, name);
        return child == null ? null : text(xml, child);
    }

    private static String text(String xml, ElementRange range) {
        return xml.substring(range.openEnd(), range.closeStart()).trim();
    }

    private static String replaceElementText(String xml, ElementRange range, String value) {
        return xml.substring(0, range.openEnd()) + value + xml.substring(range.closeStart());
    }

    private static String removeRange(String xml, ElementRange range) {
        int start = range.start;
        int end = range.end;
        while (start > 0 && (xml.charAt(start - 1) == ' ' || xml.charAt(start - 1) == '\t')) start--;
        if (start > 0 && xml.charAt(start - 1) == '\n') start--;
        else if (start > 1 && xml.charAt(start - 2) == '\r' && xml.charAt(start - 1) == '\n') start -= 2;
        while (end < xml.length() && (xml.charAt(end) == ' ' || xml.charAt(end) == '\t')) end++;
        if (end < xml.length() && xml.charAt(end) == '\r') end++;
        if (end < xml.length() && xml.charAt(end) == '\n') end++;
        return xml.substring(0, start) + xml.substring(end);
    }

    private static String insertBeforeProjectClose(String xml, String block) {
        int end = xml.lastIndexOf("</project>");
        if (end < 0) throw failure("XML_INVALID", "Closing project element not found");
        return insertAt(xml, end, block);
    }

    private static String insertAt(String xml, int position, String value) {
        return xml.substring(0, position) + value + xml.substring(position);
    }

    private static int insertionPoint(String xml, int closingTagStart) {
        int lineStart = xml.lastIndexOf('\n', closingTagStart - 1) + 1;
        for (int index = lineStart; index < closingTagStart; index++) {
            if (xml.charAt(index) != ' ' && xml.charAt(index) != '\t') return closingTagStart;
        }
        return lineStart;
    }

    private static ElementRange section(String xml, String name, int occurrence) {
        List<ElementRange> ranges = all(xml, name);
        return occurrence < ranges.size() ? ranges.get(occurrence) : null;
    }

    private static List<ElementRange> all(String xml, String name) {
        List<ElementRange> result = new ArrayList<>();
        Matcher matcher = TAG.matcher(xml);
        List<Open> stack = new ArrayList<>();
        while (matcher.find()) {
            String closing = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String tag = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            if (tag == null) continue;
            if (closing != null && !closing.isEmpty()) {
                for (int i = stack.size() - 1; i >= 0; i--) {
                    Open open = stack.get(i);
                    if (open.name.equals(tag)) {
                        stack.subList(i, stack.size()).clear();
                        if (open.name.equals(name)) result.add(new ElementRange(open.start, matcher.end(), open.contentStart,
                                matcher.start(), open.openEnd));
                        break;
                    }
                }
            } else if (!matcher.group().endsWith("/>") && !matcher.group().startsWith("<?")) {
                stack.add(new Open(tag, matcher.start(), matcher.end(), matcher.end()));
            }
        }
        result.sort(Comparator.comparingInt(ElementRange::start));
        return result;
    }

    private static List<ElementRange> children(String xml, ElementRange parent, String name) {
        return all(xml.substring(parent.contentStart, parent.closeStart), name).stream()
                .map(item -> new ElementRange(item.start + parent.contentStart, item.end + parent.contentStart,
                        item.contentStart + parent.contentStart, item.closeStart + parent.contentStart,
                        item.openEnd + parent.contentStart)).toList();
    }

    private static ElementRange child(String xml, ElementRange parent, String name) {
        return children(xml, parent, name).stream().findFirst().orElse(null);
    }

    private static boolean inside(ElementRange candidate, ElementRange container) {
        return candidate.start() > container.start() && candidate.end() < container.end();
    }

    private static String coordinate(PomEditRequest request) { return request.groupId() + ":" + request.artifactId(); }

    private static PomEditException failure(String reason, String message) { return new PomEditException(reason, message); }

    private static Indent indentation(String xml) {
        Matcher matcher = MODEL_VERSION.matcher(xml);
        if (matcher.find()) return new Indent(matcher.group(1), newline(xml));
        Matcher fallback = Pattern.compile("(?m)^([ \\t]+)<[A-Za-z][^>]*>").matcher(xml);
        if (fallback.find()) return new Indent(fallback.group(1), newline(xml));
        throw failure("INDENTATION_STYLE_NOT_DETECTED", "Unable to detect POM indentation style");
    }

    private static String newline(String xml) { return xml.contains("\r\n") ? "\r\n" : "\n"; }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String diff(String before, String after) {
        if (before.equals(after)) return "";
        String[] left = before.split("\\R", -1), right = after.split("\\R", -1);
        StringBuilder result = new StringBuilder("--- pom.xml\n+++ pom.xml\n");
        int common = Math.min(left.length, right.length);
        int index = 0;
        while (index < common && left[index].equals(right[index])) index++;
        int tail = 0;
        while (tail < left.length - index && tail < right.length - index
                && left[left.length - 1 - tail].equals(right[right.length - 1 - tail])) tail++;
        for (int i = index; i < left.length - tail; i++) result.append('-').append(left[i]).append('\n');
        for (int i = index; i < right.length - tail; i++) result.append('+').append(right[i]).append('\n');
        return result.toString();
    }

    private record Open(String name, int start, int contentStart, int openEnd) { }
    private record ElementRange(int start, int end, int contentStart, int closeStart, int openEnd) { }
    private record Indent(String token, String newline) {
        String line() { return token; }
        String line(int level) { return token.repeat(level); }
        String nl() { return newline; }
    }
}
