package pl.sca.fix.uibackend.config;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/configuration")
public class WorkspaceConfigurationController {
    private final WorkspaceConfigurationService service;
    public WorkspaceConfigurationController(WorkspaceConfigurationService service) { this.service = service; }

    @GetMapping
    public WorkspaceConfiguration get(@PathVariable UUID workspaceId) { return service.get(workspaceId); }

    @PutMapping
    public WorkspaceConfiguration update(@PathVariable UUID workspaceId,
                                         @Valid @RequestBody WorkspaceConfiguration configuration) {
        return service.update(workspaceId, configuration);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID workspaceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("workspace-" + workspaceId + "-config.json")
                .build());
        return ResponseEntity.ok().headers(headers).body(service.export(workspaceId));
    }

    @PutMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkspaceConfiguration importConfiguration(@PathVariable UUID workspaceId,
                                                       @RequestPart("file") MultipartFile file) {
        try {
            return service.importConfiguration(workspaceId, file.getBytes());
        } catch (java.io.IOException exception) {
            throw new pl.sca.fix.uibackend.workspace.WorkspaceException(
                    "CONFIGURATION_FILE_READ_FAILED", exception.getMessage());
        }
    }
}

