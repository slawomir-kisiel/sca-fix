package pl.sca.fix.uibackend.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.sca.fix.core.maven.NioFileSystemAdapter;
import pl.sca.fix.core.port.ReportDocument;
import pl.sca.fix.core.report.CanonicalReportStore;
import pl.sca.fix.core.report.ReportImportResult;
import pl.sca.fix.core.report.ScanReportImporter;

@Service
public class ReportImportService {
    private final WorkspaceRepository workspaces;
    private final ScanReportImporter importer = new ScanReportImporter();
    private final CanonicalReportStore store = new CanonicalReportStore(new NioFileSystemAdapter());

    public ReportImportService(WorkspaceRepository workspaces) { this.workspaces = workspaces; }

    public ReportImportResultView importReport(UUID workspaceId, String fileName, byte[] content, String formatHint) {
        WorkspaceEntity workspace = workspaces.findById(workspaceId).orElseThrow(() ->
                new WorkspaceException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId));
        if (content == null || content.length == 0) {
            throw new WorkspaceException("REPORT_FILE_EMPTY", "Report file is empty");
        }
        String safeName = safeFileName(fileName);
        Path root = Path.of(workspace.getRepositoryPath()).resolve("target/scafix/reports");
        Path raw = root.resolve("raw").resolve(safeName);
        Path canonical = root.resolve("canonical-report.json");
        try {
            Files.createDirectories(raw.getParent());
            Files.write(raw, content);
            ReportImportResult result = importer.importReport(new ReportDocument(content, raw, formatHint));
            store.save(canonical, result.report());
            return new ReportImportResultView(workspaceId, result.report().metadata().source(), safeName,
                    content.length, sha256(content), result.report().status().name(),
                    result.report().vulnerableLibraries().size(), result.report().cves().size(),
                    result.warnings(), result.errors(), raw.toString(), canonical.toString(), Instant.now());
        } catch (RuntimeException | java.io.IOException exception) {
            throw new WorkspaceException("REPORT_IMPORT_FAILED", exception.getMessage());
        }
    }

    private static String safeFileName(String fileName) {
        String value = fileName == null || fileName.isBlank() ? "report.json" : Path.of(fileName).getFileName().toString();
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception exception) { throw new IllegalStateException("Unable to calculate report hash", exception); }
    }
}
