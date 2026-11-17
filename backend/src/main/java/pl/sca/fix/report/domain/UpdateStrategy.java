package pl.sca.fix.report.domain;

public enum UpdateStrategy {
    DIRECT_DEPENDENCY,
    DEPENDENCY_MANAGEMENT,
    PROPERTY,
    CVE_OVERRIDE,
    DEFER,
    REJECT
}
