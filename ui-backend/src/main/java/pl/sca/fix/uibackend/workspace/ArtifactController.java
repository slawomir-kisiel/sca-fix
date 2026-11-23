package pl.sca.fix.uibackend.workspace;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ui-api/workspaces/{workspaceId}/artifacts")
public class ArtifactController {
    private final ArtifactService service;
    public ArtifactController(ArtifactService service) { this.service = service; }
    @GetMapping("/{*relativePath}")
    public ResponseEntity<Resource> download(@PathVariable UUID workspaceId, @PathVariable String relativePath) {
        ArtifactService.ArtifactResource artifact = service.get(workspaceId, relativePath);
        MediaType mediaType = mediaType(artifact.contentType());
        return ResponseEntity.ok().contentType(mediaType).contentLength(artifact.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(artifact.fileName()).build().toString())
                .body(artifact.resource());
    }
    private static MediaType mediaType(String value) { try { return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value); } catch (Exception exception) { return MediaType.APPLICATION_OCTET_STREAM; } }
}
