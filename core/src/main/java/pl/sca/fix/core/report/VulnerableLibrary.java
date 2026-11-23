package pl.sca.fix.core.report;

import java.math.BigDecimal;
import java.util.List;

public record VulnerableLibrary(String groupId, String artifactId, String version,
                                List<String> cve, Severity severity, BigDecimal cvss,
                                List<String> recommendedVersion) {
    public VulnerableLibrary {
        cve = List.copyOf(cve == null ? List.of() : cve);
        recommendedVersion = List.copyOf(recommendedVersion == null ? List.of() : recommendedVersion);
    }
}
