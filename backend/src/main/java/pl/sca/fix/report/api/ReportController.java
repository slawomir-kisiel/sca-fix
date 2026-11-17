package pl.sca.fix.report.api;

import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import pl.sca.fix.report.application.ReportContextService;
import pl.sca.fix.report.application.ReportCorrelationService;
import pl.sca.fix.report.api.ReportContextDtos.UpdateReportContextRequest;
import pl.sca.fix.report.api.ReportContextDtos.UpdateReportContextResponse;
import pl.sca.fix.report.application.ReportStorageService;
import pl.sca.fix.report.api.ReportDtos.ReportImportResponseDto;
import pl.sca.fix.report.api.ReportDtos.SecurityScanReportMetadataDto;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportStorageService storage;
    private final ReportContextService context;
    private final ReportCorrelationService correlation;

    public ReportController(ReportStorageService storage, ReportContextService context, ReportCorrelationService correlation) { this.storage = storage; this.context = context; this.correlation = correlation; }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReportImportResponseDto importReport(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(required = false) UUID repositoryId) {
        return storage.store(file, repositoryId);
    }

    @GetMapping("/{reportId}")
    public SecurityScanReportMetadataDto metadata(@PathVariable UUID reportId) { return storage.metadata(reportId); }

    @PostMapping("/{reportId}/context")
    public UpdateReportContextResponse updateContext(@PathVariable UUID reportId, @Valid @RequestBody UpdateReportContextRequest request) {
        return context.update(reportId, request);
    }

    @PostMapping("/{reportId}/correlation")
    public ReportDtos.CorrelationResponseDto correlate(@PathVariable UUID reportId) { return correlation.start(reportId); }

    @GetMapping("/{reportId}/raw")
    public ResponseEntity<ByteArrayResource> raw(@PathVariable UUID reportId) {
        byte[] content = storage.raw(reportId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("report").build().toString())
                .body(new ByteArrayResource(content));
    }
}
