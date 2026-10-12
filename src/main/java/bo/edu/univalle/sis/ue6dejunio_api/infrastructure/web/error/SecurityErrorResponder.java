package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.error;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the error body for the rejections the security filter chain decides on its own.
 *
 * <p>A role rule in {@code SecurityConfig} and a missing or unverifiable bearer token are refused
 * before the DispatcherServlet runs, so {@link GlobalExceptionHandler} never sees them and they
 * used to leave with an empty body. This answers them in the same {@link ErrorResponse} shape.
 *
 * <p>The bearer-token handlers still run first: they set the status and the {@code
 * WWW-Authenticate} challenge (RFC 6750), and only the body is added here. The exception's own
 * message is never echoed, since it can describe the token that was presented.
 *
 * <p>Not a {@code @Component}: it is wired as a bean in {@code SecurityConfig}, next to the chain
 * that uses it, so the {@code @WebMvcTest} slices that build that chain get it with it.
 */
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHENTICATED_MESSAGE = "No autenticado o sesión vencida";
    static final String ACCESS_DENIED_MESSAGE = "Acceso denegado";

    private final AuthenticationEntryPoint bearerEntryPoint =
            new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
    private final JsonMapper json;

    public SecurityErrorResponder(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, authException);
        writeBody(request, response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_MESSAGE);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        bearerAccessDenied.handle(request, response, accessDeniedException);
        writeBody(request, response, HttpStatus.FORBIDDEN, ACCESS_DENIED_MESSAGE);
    }

    private void writeBody(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ErrorResponse body =
                ErrorResponse.of(
                        status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.writeValueAsString(body));
        response.getWriter().flush();
    }
}
