package pl.sca.fix.common.process;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SecretRedactor {
    private static final Pattern TOKEN = Pattern.compile("(?i)(nvdApiKey|token|password|passwd|secret|authorization)(\\s*[:=]\\s*)([^\\s]+)");

    public String redact(String value) {
        return value == null ? null : TOKEN.matcher(value).replaceAll("$1$2[REDACTED]");
    }
}
