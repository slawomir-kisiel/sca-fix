package pl.sca.fix.integration.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MavenDependencyTreeParserTest {

    @Test
    void parsesOnlyCoordinatesFromDependencyTreeSection() {
        String output = """
                [INFO] Scanning for projects...
                [WARNING] problem while resolving org.apache.ant.wso2:ant:jar:1.7.0.wso2v1
                [INFO] --- dependency:3.7.0:tree (default-cli) @ web ---
                [INFO] com.coig.mid.light:web:war:0.0.1-SNAPSHOT
                [INFO] +- junit:junit:jar:4.11:test
                [INFO] |  \\- org.hamcrest:hamcrest-core:jar:1.3:test
                [INFO] ------------------------------------------------------------------------
                [INFO] BUILD SUCCESS
                """;

        List<MavenCoordinate> result = new MavenDependencyTreeParser().parse(output);

        assertThat(result).extracting(MavenCoordinate::groupId)
                .containsExactly("com.coig.mid.light", "junit", "org.hamcrest");
        assertThat(result).extracting(MavenCoordinate::artifactId)
                .containsExactly("web", "junit", "hamcrest-core");
        assertThat(result).extracting(MavenCoordinate::depth)
                .containsExactly(0, 1, 2);
    }
}
