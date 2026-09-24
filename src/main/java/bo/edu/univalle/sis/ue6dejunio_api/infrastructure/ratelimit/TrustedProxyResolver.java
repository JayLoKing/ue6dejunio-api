package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ClientIpResolver} for deployments behind a reverse proxy. Honors {@code X-Forwarded-For}
 * ONLY when the immediate {@code remoteAddr} is one of the configured trusted proxies, taking the
 * first (left-most, closest-to-client) hop of the header. Falls back to {@code remoteAddr} in every
 * other case, and never throws.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.ip-resolver", havingValue = "trusted-proxy")
public class TrustedProxyResolver implements ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final RateLimitProperties properties;

    public TrustedProxyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IP;
        }
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return UNKNOWN_IP;
        }

        List<String> trustedProxies = properties.getTrustedProxies();
        if (trustedProxies == null || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }

        String firstHop = forwardedFor.split(",")[0].trim();
        return firstHop.isEmpty() ? remoteAddr : firstHop;
    }
}
