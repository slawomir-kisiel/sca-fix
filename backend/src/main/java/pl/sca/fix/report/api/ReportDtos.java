package pl.sca.fix.report.api;

import java.time.Instant;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() { }

    public record JobReferenceDto(UUID jobId, String status) { }

    public record ReportImportResponseDto(UUID reportId, String status, String source, String format,
                                          String formatVersion, JobReferenceDto job) { }

    public record SecurityScanReportMetadataDto(UUID reportId, UUID repositoryId, String originalFilename,
                                                String contentType, String source, String format,
                                                String formatVersion, Instant importedAt, Instant scanDate,
                                                long sizeBytes, String sha256, String status) { }

    public record CorrelationResponseDto(UUID reportId, String status, long matchedCount,
                                         long ambiguousCount, long orphanCount, JobReferenceDto job) { }
}
