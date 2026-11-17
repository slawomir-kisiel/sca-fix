package pl.sca.fix.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomParserTest {
    @TempDir Path temp;

    @Test
    void parsesCoordinatesParentAndModules() throws Exception {
        Path pom = temp.resolve("pom.xml");
        Files.writeString(pom, """
                <project xmlns=\"http://maven.apache.org/POM/4.0.0\">
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>com.example</groupId><artifactId>parent</artifactId><version>1</version></parent>
                  <artifactId>service-a</artifactId>
                  <packaging>pom</packaging>
                  <modules><module>module-a</module><module>module-b</module></modules>
                </project>
                """);

        PomDescriptor result = new PomParser().parse(temp, pom);

        assertThat(result.groupId()).isEqualTo("com.example");
        assertThat(result.artifactId()).isEqualTo("service-a");
        assertThat(result.version()).isEqualTo("1");
        assertThat(result.parent().artifactId()).isEqualTo("parent");
        assertThat(result.modules()).containsExactly("module-a", "module-b");
    }
}
