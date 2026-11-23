package pl.sca.fix.core.report;

import java.math.BigDecimal;
import java.util.List;

public record CveDetails(String signature, BigDecimal cvss, Severity severity,
                         String title, String description, String recommendation,
                         List<String> references) {
    public CveDetails {
        references = List.copyOf(references == null ? List.of() : references);
    }
}
