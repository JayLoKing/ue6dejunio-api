package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link DirectRemoteAddrResolver} and {@link TrustedProxyResolver}, covering the
 * "Pluggable Client-IP Resolution" spec requirement, including the fail-safe scenario.
 */
class ClientIpResolverTest {

    @Test
    void directResolver_usesRemoteAddrDirectly() {
        DirectRemoteAddrResolver resolver = new DirectRemoteAddrResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");
        // Even if an XFF header is present, the direct resolver must ignore it.
        request.addHeader("X-Forwarded-For", "9.9.9.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void directResolver_blankRemoteAddr_fallsBackToUnknown_neverThrows() {
        DirectRemoteAddrResolver resolver = new DirectRemoteAddrResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");

        assertThatCode(() -> resolver.resolve(request)).doesNotThrowAnyException();
        assertThat(resolver.resolve(request)).isEqualTo(ClientIpResolver.UNKNOWN_IP);
    }

    @Test
    void directResolver_nullRequest_failsSafe() {
        DirectRemoteAddrResolver resolver = new DirectRemoteAddrResolver();

        assertThatCode(() -> resolver.resolve(null)).doesNotThrowAnyException();
        assertThat(resolver.resolve(null)).isEqualTo(ClientIpResolver.UNKNOWN_IP);
    }

    @Test
    void trustedProxyResolver_untrustedRemoteAddr_ignoresForwardedForHeader() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedProxies(List.of("10.0.0.1"));
        TrustedProxyResolver resolver = new TrustedProxyResolver(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.9"); // not in trusted-proxies
        request.addHeader("X-Forwarded-For", "1.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.9");
    }

    @Test
    void trustedProxyResolver_trustedRemoteAddr_usesFirstForwardedForHop() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedProxies(List.of("10.0.0.1"));
        TrustedProxyResolver resolver = new TrustedProxyResolver(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.99, 10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.99");
    }

    @Test
    void trustedProxyResolver_trustedRemoteAddr_noForwardedForHeader_fallsBackToRemoteAddr() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedProxies(List.of("10.0.0.1"));
        TrustedProxyResolver resolver = new TrustedProxyResolver(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void trustedProxyResolver_nullRequest_failsSafe() {
        RateLimitProperties properties = new RateLimitProperties();
        TrustedProxyResolver resolver = new TrustedProxyResolver(properties);

        assertThatCode(() -> resolver.resolve(null)).doesNotThrowAnyException();
        assertThat(resolver.resolve(null)).isEqualTo(ClientIpResolver.UNKNOWN_IP);
    }
}
