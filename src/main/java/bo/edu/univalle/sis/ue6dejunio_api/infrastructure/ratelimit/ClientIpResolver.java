package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP used as a rate-limiting key. Implementations MUST fail safe: resolution
 * failure or ambiguity must never throw, it must return a deterministic fallback value instead.
 */
public interface ClientIpResolver {

    /** Fallback key used whenever a resolver cannot determine a usable client IP. */
    String UNKNOWN_IP = "unknown";

    String resolve(HttpServletRequest request);
}
