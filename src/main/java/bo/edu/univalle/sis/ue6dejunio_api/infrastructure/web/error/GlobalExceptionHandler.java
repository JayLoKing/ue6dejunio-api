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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    /** Postgres for "a unique constraint said no". The one integrity failure the caller caused. */
    private static final String UNIQUE_VIOLATION = "23505";

    /** Far past any real wrapping: Hibernate and Spring together add three or four links. */
    private static final int MAX_CAUSE_DEPTH = 20;

    /**
     * A row the database itself turned away — a conflict if the caller caused it, a bug if we did.
     *
     * <p>Where a rule is held by a constraint rather than by a look-before-you-write, the caller
     * who loses the race learns about it from Postgres instead of from the service. Left to the
     * catch-all that arrives as a 500: the caller told the server broke, when what happened is
     * that someone else got there first.
     *
     * <p>But {@link DataIntegrityViolationException} is the parent of every integrity failure
     * Spring translates. A NOT NULL, a foreign key or a check violation is this code writing a row
     * it had no business writing; answering those 409 would send the caller to resolve a conflict
     * that is not theirs, and they would retry forever against a bug that had stopped being
     * logged. So the two are told apart, and only the duplicate is the caller's.
     *
     * <p>The signal is the SQL state, not the exception type, and that is not a preference. Spring
     * translates the same Postgres failure into different types depending on who caught it:
     * JdbcTemplate produces {@code DuplicateKeyException}, Hibernate wraps its own and produces
     * the plain parent. Every write in this application goes through JPA, so a handler keyed on
     * the subclass would answer 500 to every real duplicate. Both paths are pinned by tests.
     *
     * <p>The duplicate is logged at WARN rather than ERROR — losing a race is not an outage — and
     * without the exception, unlike every other line this class writes. Postgres spells a unique
     * violation out as {@code Key (email)=(someone@example.com) already exists}: the values that
     * collided are in the message, and here those values are students and teachers. The method and
     * the path say a duplicate happened and where, which is what the line is for.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest req) {
        if (!isDuplicate(ex)) {
            logTheFailure(ex, req);
            return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Error interno del servidor", req);
        }
        log.warn("Duplicate rejected by the database on {} {}",
            req.getMethod(), req.getRequestURI());
        return build(HttpStatus.CONFLICT, "Conflict",
            "El registro entra en conflicto con uno existente", req);
    }

    /**
     * Whether the database turned the row away for being a duplicate.
     *
     * <p>An integrity failure that names no SQL state is not evidence of a conflict, so it is not
     * treated as one: guessing 409 would hand a server bug back as the caller's problem.
     *
     * <p>The walk is bounded rather than guarded against a self-cause. Java already refuses to let
     * an exception cause itself, so that guard covers the case that cannot happen while a cycle
     * through two exceptions — which nothing here builds, but nothing forbids either — would spin
     * forever inside the handler that exists to keep failures from spreading.
     */
    private static boolean isDuplicate(DataIntegrityViolationException ex) {
        if (ex instanceof DuplicateKeyException) {
            return true;
        }
        Throwable cause = ex;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
     *
     * <p>Hibernate throws this same type at flush when an entity fails bean validation, which
     * would be a server fault wearing a caller's clothes: the path would name an entity field and
     * this would answer 400 for a mapping bug. It cannot happen here — no entity in this project
     * carries a {@code jakarta.validation} annotation, checked rather than assumed. Should one
     * ever gain them, this handler has to tell the two apart before that stays true.
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
                // Spring naming the status does not make it the caller's fault: a request that
                // times out waiting on an async result comes through here as a 503. What decides
                // whether a failure is worth a log line is the status, not who classified it.
                if (status.is5xxServerError()) {
                    logTheFailure(ex, req);
                }
                return build(status, status.getReasonPhrase(), status.getReasonPhrase(), req);
            }
        }
        logTheFailure(ex, req);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            "Error interno del servidor", req);
    }

    /**
     * The only place a server failure's cause survives.
     *
     * <p>What the caller gets back names the status and nothing more, on purpose — the internals
     * are not theirs to see. So without this line an unexpected failure leaves no stacktrace
     * anywhere and no way to tell which request caused it.
     *
     * <p>Client errors are deliberately not logged here: they are answered with the status they
     * deserve, and recording them at ERROR would bury the failures worth reading under other
     * people's typos. The URI is taken without its query string, which is also what keeps a reset
     * token out of the log.
     */
    private static void logTheFailure(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(
            ErrorResponse.of(status.value(), error, includeMessage ? message : null, req.getRequestURI())
        );
    }
}
