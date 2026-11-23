package pl.sca.fix.uibackend.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportImportResultView(UUID workspaceId, String format, String sourceFileName,
                                     long sizeBytes, String sha256, String status,
                                     int vulnerableLibraries, int cves, List<String> warnings,
                                     List<String> errors, String rawPath, String canonicalPath,
                                     Instant importedAt) {}
