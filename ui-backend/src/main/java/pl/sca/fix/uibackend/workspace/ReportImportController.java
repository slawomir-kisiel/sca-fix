package pl.sca.fix.uibackend.workspace;

import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/reports")
public class ReportImportController {
    private final ReportImportService service;
    public ReportImportController(ReportImportService service) { this.service = service; }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportImportResultView importReport(@PathVariable UUID workspaceId,
                                               @RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) String format) {
        try {
            return service.importReport(workspaceId, file.getOriginalFilename(), file.getBytes(), format);
        } catch (IOException exception) {
            throw new WorkspaceException("REPORT_FILE_READ_FAILED", exception.getMessage());
        }
    }
}
