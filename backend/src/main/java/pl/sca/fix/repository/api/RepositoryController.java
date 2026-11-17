package pl.sca.fix.repository.api;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.sca.fix.repository.api.RepositoryDtos.RepositoryDetailsDto;
import pl.sca.fix.repository.api.RepositoryDtos.RepositorySummaryDto;
import pl.sca.fix.repository.application.RepositoryQueryService;
import pl.sca.fix.repository.application.RepositoryRegistrationService;
import pl.sca.fix.repository.api.RepositoryDtos.RegisterLocalRepositoryRequest;
import pl.sca.fix.repository.api.RepositoryDtos.RegisterGitRepositoryRequest;
import pl.sca.fix.repository.api.RepositoryDtos.RegisterRepositoryResponse;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {
    private final RepositoryQueryService service;
    private final RepositoryRegistrationService registration;

    public RepositoryController(RepositoryQueryService service, RepositoryRegistrationService registration) {
        this.service = service;
        this.registration = registration;
    }

    @org.springframework.web.bind.annotation.PostMapping("/local")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public RepositorySummaryDto registerLocal(@Valid @org.springframework.web.bind.annotation.RequestBody RegisterLocalRepositoryRequest request) {
        return registration.registerLocal(request);
    }

    @org.springframework.web.bind.annotation.PostMapping("/git")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public RegisterRepositoryResponse registerGit(@Valid @org.springframework.web.bind.annotation.RequestBody RegisterGitRepositoryRequest request) {
        return registration.startGitClone(request);
    }

    @GetMapping
    public List<RepositorySummaryDto> list() { return service.list(); }

    @GetMapping("/{repositoryId}")
    public RepositoryDetailsDto details(@PathVariable UUID repositoryId) {
        return service.details(repositoryId);
    }
}
