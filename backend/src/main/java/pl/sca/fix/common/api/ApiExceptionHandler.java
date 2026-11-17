package pl.sca.fix.common.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import pl.sca.fix.job.application.JobNotFoundException;
import pl.sca.fix.workspace.application.WorkspacePathException;
import pl.sca.fix.repository.application.RepositoryNotFoundException;
import pl.sca.fix.repository.application.RepositoryRegistrationException;
import pl.sca.fix.integration.git.GitCheckoutException;
import pl.sca.fix.project.application.ProjectDiscoveryException;
import pl.sca.fix.report.application.ReportNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(JobNotFoundException.class)
    ResponseEntity<?> jobNotFound(JobNotFoundException exception, HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(": JOB_NOT_FOUND\n\n");
        }
        return error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ReportNotFoundException.class)
    ResponseEntity<ApiError> reportNotFound(ReportNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(WorkspacePathException.class)
    ResponseEntity<ApiError> workspacePath(WorkspacePathException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(RepositoryNotFoundException.class)
    ResponseEntity<ApiError> repositoryNotFound(RepositoryNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "REPOSITORY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({RepositoryRegistrationException.class, GitCheckoutException.class, ProjectDiscoveryException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> repositoryRegistration(RuntimeException exception) {
        String code = exception instanceof ProjectDiscoveryException project ? project.getCode()
                : exception instanceof RepositoryRegistrationException registration
                ? registration.getCode()
                : exception instanceof GitCheckoutException git ? git.getCode() : "INVALID_REQUEST";
        return error(HttpStatus.BAD_REQUEST, code, exception.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message));
    }
}
