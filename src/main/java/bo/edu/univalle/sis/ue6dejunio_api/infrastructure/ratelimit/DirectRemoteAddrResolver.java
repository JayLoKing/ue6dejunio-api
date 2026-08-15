package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link ClientIpResolver}: trusts the servlet container's {@code remoteAddr} directly,
 * ignoring any proxy headers. Safe when there is no reverse proxy in front of the application.
 */
@Component
@ConditionalOnProperty(
    name = "app.security.rate-limit.ip-resolver",
    havingValue = "direct",
    matchIfMissing = true)
public class DirectRemoteAddrResolver implements ClientIpResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IP;
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? UNKNOWN_IP : remoteAddr;
    }
}
