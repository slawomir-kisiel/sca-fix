package pl.sca.fix.core.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenSettingsProxyResolverTest {
    @TempDir Path temp;

    @Test
    void readsFirstActiveProxy() throws Exception {
        Path settings = temp.resolve("settings.xml");
        Files.writeString(settings, "<settings><proxies>"
                + "<proxy><active>false</active><host>bad</host><port>1</port></proxy>"
                + "<proxy><active>true</active><host>proxy.test</host><port>8080</port>"
                + "<username>u</username><password>p</password></proxy>"
                + "</proxies></settings>");
        var result = new MavenSettingsProxyResolver().resolve(settings);
        assertTrue(result.isPresent());
        assertEquals("proxy.test", result.get().host());
        assertEquals(8080, result.get().port());
        assertEquals("u", result.get().username());
    }
}
