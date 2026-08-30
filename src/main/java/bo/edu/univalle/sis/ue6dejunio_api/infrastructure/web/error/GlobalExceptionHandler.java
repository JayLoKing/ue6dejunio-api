package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.error;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidResetTokenException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

    private final boolean includeMessage;
    private final boolean includeBindingErrors;

    public GlobalExceptionHandler(
        @Value("${server.error.include-message:always}") String includeMessage,
        @Value("${server.error.include-binding-errors:always}") String includeBindingErrors
    ) {
        this.includeMessage = !"never".equalsIgnoreCase(includeMessage);
        this.includeBindingErrors = !"never".equalsIgnoreCase(includeBindingErrors);
    }

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

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    /**
     * A row the database itself turned away, answered as the conflict it is.
     *
     * <p>Where a rule is held by a constraint rather than by a look-before-you-write, the caller
     * who loses the race learns about it from Postgres instead of from the service. Left to the
     * catch-all that arrives as a 500 — the caller told the server broke, when what happened is
     * that someone else got there first. The message is deliberately the service's own wording:
     * the exception carries the constraint name, and a constraint name tells the caller nothing
     * they can act on while telling an attacker the shape of the tables.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict",
            "El registro entra en conflicto con uno existente", req);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetToken(InvalidResetTokenException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        return badRequest(
            ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> detail(
                    fe.getField(),
                    fe.getDefaultMessage() == null ? "inválido" : fe.getDefaultMessage()))
                .toList(),
            req);
    }

    /**
     * A request parameter the API refuses, answered 400 rather than 500.
     *
     * <p>Every listing pages with {@code @Min}/{@code @Max} on its parameters and
     * {@code @Validated} on the controller class. That pair validates through the AOP proxy and
     * throws this instead of one of Spring's own {@code ErrorResponse} types, so without a handler
     * it fell through to the catch-all — and asking for more rows than the ceiling allows was
     * reported as a server failure across all fifteen controllers that page this way.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest req) {
        return badRequest(
            ex.getConstraintViolations().stream()
                .map(v -> detail(parameterOf(v.getPropertyPath().toString()), v.getMessage()))
                .toList(),
            req);
    }

    /**
     * The parameter the caller sent. A method violation names its path as {@code list.limit} —
     * the method it was validated on, then the parameter — and only the last segment is something
     * the caller can act on.
     */
    private static String parameterOf(String propertyPath) {
        int lastSeparator = propertyPath.lastIndexOf('.');
        return lastSeparator < 0 ? propertyPath : propertyPath.substring(lastSeparator + 1);
    }

    private static Map<String, String> detail(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    private ResponseEntity<ErrorResponse> badRequest(List<Map<String, String>> details,
                                                     HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.withDetails(
            HttpStatus.BAD_REQUEST.value(), "Bad Request",
            includeMessage ? "Datos de entrada inválidos" : null, req.getRequestURI(),
            includeBindingErrors ? details : List.of()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        // Spring's own web exceptions already carry the correct status: 404 for a path with no
        // handler, 405 for a wrong method, 415 for an unsupported media type. Flattening them into
        // 500 would report the caller's mistake as a server failure and make every unmapped URL
        // look like an outage.
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.resolve(springError.getStatusCode().value());
            if (status != null) {
                return build(status, status.getReasonPhrase(), status.getReasonPhrase(), req);
            }
        }
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            "Error interno del servidor", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(
            ErrorResponse.of(status.value(), error, includeMessage ? message : null, req.getRequestURI())
        );
    }
}
