package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces per-request throttling before authentication runs. Order of checks per request:
 * endpoint-specific limiter (login / change-password), then the global per-IP limiter. Any
 * rejection short-circuits with {@code 429 Too Many Requests} + {@code Retry-After}, identical
 * regardless of whether the submitted email exists, and never invokes downstream authentication.
 *
 * <p>Deliberately NOT a {@code @Component}: {@link OncePerRequestFilter} implements {@code
 * jakarta.servlet.Filter}, and {@code @WebMvcTest} slices auto-register any {@code Filter}-typed
 * {@code @Component} bean found anywhere on the classpath, which would break every unrelated
 * {@code @WebMvcTest} that does not also provide this filter's dependencies. It is instead wired
 * explicitly as a {@code @Bean} in {@link
 * bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config.SecurityConfig}.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String CHANGE_PASSWORD_PATH = "/api/auth/change-password";
    private static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";
    private static final String RESET_PASSWORD_PATH = "/api/auth/reset-password";
    private static final String UNKNOWN_SUBJECT = "unknown";
    private static final String TOO_MANY_REQUESTS_BODY =
            "{\"message\":\"Too many requests. Please try again later.\"}";

    private final RateLimitProperties properties;
    private final ClientIpResolver ipResolver;
    private final RateLimitStore store;
    // Deliberately NOT a Spring-managed bean: this app's Boot 4 auto-configuration wires the new
    // Jackson 3 (tools.jackson) JsonMapper for MVC, not a classic com.fasterxml ObjectMapper bean.
    // A private, lightweight instance is sufficient for this filter's own key-only JSON parsing.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitingFilter(
            RateLimitProperties properties, ClientIpResolver ipResolver, RateLimitStore store) {
        this.properties = properties;
        this.ipResolver = ipResolver;
        this.store = store;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveIpSafely(request);
        HttpServletRequest effectiveRequest = request;

        boolean isLogin = isJsonPostTo(request, LOGIN_PATH);
        boolean isChangePassword = isJsonPostTo(request, CHANGE_PASSWORD_PATH);
        boolean isForgotPassword = isJsonPostTo(request, FORGOT_PASSWORD_PATH);
        boolean isResetPassword = isJsonPostTo(request, RESET_PASSWORD_PATH);

        String specificKey = null;
        RateLimitProperties.Bucket specificConfig = null;

        if (isLogin) {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            effectiveRequest = cached;
            specificKey = "login:" + ip + ":" + extractEmail(cached.getCachedBody());
            specificConfig = properties.getLogin();
        } else if (isChangePassword) {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            effectiveRequest = cached;
            specificKey = "cpw:" + ip + ":" + extractUserId(request);
            specificConfig = properties.getChangePassword();
        } else if (isForgotPassword) {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            effectiveRequest = cached;
            specificKey = "fpw:" + ip + ":" + extractEmail(cached.getCachedBody());
            specificConfig = properties.getForgotPassword();
        } else if (isResetPassword) {
            specificKey = "rpw:" + ip;
            specificConfig = properties.getResetPassword();
        }

        if (specificKey != null) {
            ConsumptionProbe probe = tryConsume(specificKey, specificConfig);
            if (!probe.isConsumed()) {
                writeTooManyRequests(response, probe);
                return;
            }
        }

        ConsumptionProbe globalProbe = tryConsume("global:" + ip, properties.getGlobal());
        if (!globalProbe.isConsumed()) {
            writeTooManyRequests(response, globalProbe);
            return;
        }

        filterChain.doFilter(effectiveRequest, response);
    }

    private String resolveIpSafely(HttpServletRequest request) {
        try {
            String ip = ipResolver.resolve(request);
            return (ip == null || ip.isBlank()) ? ClientIpResolver.UNKNOWN_IP : ip;
        } catch (RuntimeException ex) {
            LOG.warn(
                    "Client IP resolution failed, falling back to '{}'",
                    ClientIpResolver.UNKNOWN_IP,
                    ex);
            return ClientIpResolver.UNKNOWN_IP;
        }
    }

    private boolean isJsonPostTo(HttpServletRequest request, String path) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && path.equals(request.getRequestURI())
                && isJsonContentType(request.getContentType());
    }

    private boolean isJsonContentType(String contentType) {
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private String extractEmail(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String email = node.path("email").asText(null);
            return (email == null || email.isBlank())
                    ? UNKNOWN_SUBJECT
                    : email.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return UNKNOWN_SUBJECT;
        }
    }

    /**
     * Extracts the JWT subject from the {@code Authorization} header WITHOUT verifying the
     * signature — used only as a rate-limiting key before real authentication runs. Any parsing
     * failure falls back to {@link #UNKNOWN_SUBJECT} rather than throwing.
     */
    private String extractUserId(HttpServletRequest request) {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return UNKNOWN_SUBJECT;
            }
            String[] parts = header.substring(7).trim().split("\\.");
            if (parts.length < 2) {
                return UNKNOWN_SUBJECT;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(payload);
            String subject = node.path("sub").asText(null);
            return (subject == null || subject.isBlank()) ? UNKNOWN_SUBJECT : subject;
        } catch (Exception ex) {
            return UNKNOWN_SUBJECT;
        }
    }

    private ConsumptionProbe tryConsume(String key, RateLimitProperties.Bucket config) {
        Bandwidth bandwidth =
                Bandwidth.builder()
                        .capacity(config.getCapacity())
                        .refillGreedy(config.getRefillPerMinute(), Duration.ofMinutes(1))
                        .build();
        Bucket bucket = store.resolveBucket(key, bandwidth);
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private void writeTooManyRequests(HttpServletResponse response, ConsumptionProbe probe)
            throws IOException {
        long retryAfterSeconds =
                Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(TOO_MANY_REQUESTS_BODY);
        response.getWriter().flush();
    }
}
