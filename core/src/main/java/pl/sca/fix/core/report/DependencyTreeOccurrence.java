package pl.sca.fix.core.report;

public record DependencyTreeOccurrence(String projectId, String pathId, String vulnerableNodeId,
                                       int depth, String previousNodeId, String resolvedVersion,
                                       String source) { }
