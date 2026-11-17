package pl.sca.fix.workspace.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.workspace.application.WorkspaceConfigurationService;
import pl.sca.fix.workspace.domain.WorkspaceConfig;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceConfigurationService service;

    public WorkspaceController(WorkspaceConfigurationService service) { this.service = service; }

    @GetMapping
    public WorkspaceConfigResponse get() { return WorkspaceConfigResponse.from(service.get()); }

    @PutMapping
    public WorkspaceConfigResponse update(@Valid @RequestBody UpdateWorkspaceConfigRequest request) {
        return WorkspaceConfigResponse.from(service.update(request.rootPath()));
    }

    @PostMapping("/ensure-directory")
    @ResponseStatus(HttpStatus.OK)
    public WorkspaceConfigResponse ensureDirectory() {
        return WorkspaceConfigResponse.from(service.ensureDirectory());
    }

    public record UpdateWorkspaceConfigRequest(@NotBlank String rootPath) { }

    public record WorkspaceConfigResponse(String rootPath, String effectivePath, boolean exists,
                                          boolean writable, boolean defaultValue) {
        static WorkspaceConfigResponse from(WorkspaceConfig config) {
            return new WorkspaceConfigResponse(config.rootPath(), config.effectivePath().toString(),
                    config.exists(), config.writable(), config.defaultValue());
        }
    }
}
