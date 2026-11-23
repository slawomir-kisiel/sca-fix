package pl.sca.fix.core.port;

import java.nio.file.Path;
import java.util.Objects;

/** Request to generate an effective POM for exactly one Maven project. */
public record MavenEffectivePomRequest(Path pomFile, Path outputFile) {
    public MavenEffectivePomRequest {
        Objects.requireNonNull(pomFile, "pomFile must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");
    }
}
