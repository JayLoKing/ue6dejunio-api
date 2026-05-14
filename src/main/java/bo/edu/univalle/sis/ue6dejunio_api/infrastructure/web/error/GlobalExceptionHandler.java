package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.error;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleBadCredentials(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Credenciales inválidas", req);
    }

    @ExceptionHandler(UserInactiveException.class)
    public ResponseEntity<ErrorResponse> handleInactive(UserInactiveException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", "Acceso denegado", req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() == null ? "inválido" : fe.getDefaultMessage()
            ))
            .toList();
        ErrorResponse body = ErrorResponse.withDetails(
            HttpStatus.BAD_REQUEST.value(), "Bad Request",
            "Datos de entrada inválidos", req.getRequestURI(), details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            "Error interno del servidor", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(
            ErrorResponse.of(status.value(), error, message, req.getRequestURI())
        );
    }
}
