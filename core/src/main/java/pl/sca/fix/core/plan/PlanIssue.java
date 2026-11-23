package pl.sca.fix.core.plan;

import java.util.Objects;

/** A structured, serializable warning or error attached to a generated plan. */
public record PlanIssue(String reason, String message) {
    public PlanIssue {
        reason = required(reason, "reason");
        message = required(message, "message");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
