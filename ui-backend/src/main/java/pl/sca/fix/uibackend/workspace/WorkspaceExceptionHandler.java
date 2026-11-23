package pl.sca.fix.uibackend.workspace;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class WorkspaceExceptionHandler {
    @ExceptionHandler(WorkspaceException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handle(WorkspaceException exception) {
        return Map.of("code", exception.code(), "message", exception.getMessage(), "timestamp", Instant.now());
    }
}

