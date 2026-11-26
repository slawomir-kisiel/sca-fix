package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.util.Objects;

/** Parameters for generating an OWASP Dependency-Check aggregate report. */
public record MavenOwaspReportRequest(
        Path pomFile, Path outputDirectory, String pluginVersion,
        String nvdApiServerId, String ossIndexServerId, String nvdApiKey) {
    public MavenOwaspReportRequest {
        pomFile = Objects.requireNonNull(pomFile, "pomFile must not be null");
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        pluginVersion = required(pluginVersion, "pluginVersion");
        nvdApiServerId = required(nvdApiServerId, "nvdApiServerId");
        ossIndexServerId = required(ossIndexServerId, "ossIndexServerId");
        nvdApiKey = nvdApiKey == null || nvdApiKey.isBlank() ? null : nvdApiKey.trim();
    }

    public Path outputFile() { return outputDirectory.resolve("dependency-check-report.json"); }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
