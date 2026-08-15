package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Pure unit tests for {@link RateLimitingFilter}: no Spring context, no DB, no Docker — drives
 * the filter directly with {@link MockHttpServletRequest}/{@link MockHttpServletResponse}. Covers
 * the spec's throttling, enumeration-safety, filter-ordering, and fail-safe requirements.
 */
class RateLimitingFilterTest {

    private static final String FIXED_IP = "203.0.113.10";

    private RateLimitProperties properties;
    private InMemoryBucketStore store;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.getLogin().setCapacity(5);
        properties.getLogin().setRefillPerMinute(5);
        properties.getGlobal().setCapacity(100);
        properties.getGlobal().setRefillPerMinute(100);
        properties.getChangePassword().setCapacity(5);
        properties.getChangePassword().setRefillPerMinute(5);
        store = new InMemoryBucketStore();
    }

    private RateLimitingFilter filter(ClientIpResolver ipResolver) {
        return new RateLimitingFilter(properties, ipResolver, store);
    }

    private static ClientIpResolver fixedIp(String ip) {
        return request -> ip;
    }

    private static MockHttpServletRequest loginRequest(String email) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(
            ("{\"email\":\"" + email + "\",\"password\":\"secret123\"}")
                .getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static MockHttpServletRequest changePasswordRequest(String subject) {
        MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/api/auth/change-password");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(
            "{\"currentPassword\":\"old12345\",\"newPassword\":\"new12345\"}"
                .getBytes(StandardCharsets.UTF_8));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + fakeJwt(subject));
        return request;
    }

    /** Builds a syntactically valid (but unsigned) JWT — the filter never verifies signatures. */
    private static String fakeJwt(String subject) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload =
            encoder.encodeToString(
                ("{\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    private static FilterChain countingChain(AtomicInteger counter) {
        return (req, res) -> counter.incrementAndGet();
    }

    @Test
    void sixthLoginAttempt_sameIpAndEmail_returns429WithRetryAfter() throws Exception {
        AtomicInteger downstreamInvocations = new AtomicInteger();
        RateLimitingFilter filter = filter(fixedIp(FIXED_IP));
        FilterChain chain = countingChain(downstreamInvocations);

        for (int i = 1; i <= 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(loginRequest("user@example.com"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200); // untouched by filter -> passed
        }
        assertThat(downstreamInvocations.get()).isEqualTo(5);

        MockHttpServletResponse sixthResponse = new MockHttpServletResponse();
        filter.doFilter(loginRequest("user@example.com"), sixthResponse, chain);

        assertThat(sixthResponse.getStatus()).isEqualTo(429);
        assertThat(sixthResponse.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
        // Rejected request must never reach downstream (AuthService is never invoked).
        assertThat(downstreamInvocations.get()).isEqualTo(5);
    }

    @Test
    void enumerationSafe_existingAndNonExistingEmail_identical429() throws Exception {
        RateLimitingFilter filter = filter(fixedIp(FIXED_IP));
        FilterChain chain = countingChain(new AtomicInteger());

        // Exhaust the key for the existing email.
        for (int i = 0; i < 5; i++) {
            filter.doFilter(loginRequest("existing@example.com"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse existingResponse = new MockHttpServletResponse();
        filter.doFilter(loginRequest("existing@example.com"), existingResponse, chain);

        // A brand-new key for a non-existing email, exhausted the same way.
        for (int i = 0; i < 5; i++) {
            filter.doFilter(loginRequest("nonexisting@example.com"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse nonExistingResponse = new MockHttpServletResponse();
        filter.doFilter(loginRequest("nonexisting@example.com"), nonExistingResponse, chain);

        assertThat(existingResponse.getStatus()).isEqualTo(nonExistingResponse.getStatus()).isEqualTo(429);
        assertThat(existingResponse.getContentAsString())
            .isEqualTo(nonExistingResponse.getContentAsString());
        assertThat(existingResponse.getContentType()).isEqualTo(nonExistingResponse.getContentType());
    }

    @Test
    void globalLimiter_exceededAcrossEndpoints_returns429() throws Exception {
        properties.getGlobal().setCapacity(3);
        properties.getGlobal().setRefillPerMinute(3);
        RateLimitingFilter filter = filter(fixedIp("198.51.100.20"));
        FilterChain chain = countingChain(new AtomicInteger());

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/me");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest fourth = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();
        filter.doFilter(fourth, fourthResponse, chain);

        assertThat(fourthResponse.getStatus()).isEqualTo(429);
        assertThat(fourthResponse.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
    }

    @Test
    void changePassword_exceededForKey_returns429() throws Exception {
        properties.getChangePassword().setCapacity(2);
        properties.getChangePassword().setRefillPerMinute(2);
        RateLimitingFilter filter = filter(fixedIp(FIXED_IP));
        FilterChain chain = countingChain(new AtomicInteger());

        for (int i = 0; i < 2; i++) {
            filter.doFilter(
                changePasswordRequest("11111111-1111-1111-1111-111111111111"),
                new MockHttpServletResponse(),
                chain);
        }
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(
            changePasswordRequest("11111111-1111-1111-1111-111111111111"),
            thirdResponse,
            chain);

        assertThat(thirdResponse.getStatus()).isEqualTo(429);
        assertThat(thirdResponse.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
    }

    @Test
    void ipResolverThrows_filterFailsSafe_doesNotCrashRequest() throws Exception {
        ClientIpResolver throwingResolver =
            request -> {
                throw new IllegalStateException("boom");
            };
        RateLimitingFilter filter = filter(throwingResolver);
        AtomicInteger downstreamInvocations = new AtomicInteger();
        FilterChain chain = countingChain(downstreamInvocations);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(loginRequest("safe@example.com"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(downstreamInvocations.get()).isEqualTo(1);
    }

    @Test
    void underLimitLogin_bodyStillReadableDownstream_regression() throws Exception {
        RateLimitingFilter filter = filter(fixedIp(FIXED_IP));
        AtomicInteger downstreamInvocations = new AtomicInteger();
        String[] capturedBody = new String[1];
        FilterChain chain =
            (req, res) -> {
                downstreamInvocations.incrementAndGet();
                capturedBody[0] =
                    new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            };

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(loginRequest("director@ue6.bo"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(downstreamInvocations.get()).isEqualTo(1);
        assertThat(capturedBody[0]).contains("director@ue6.bo").contains("secret123");
    }

    @Test
    void disabledFeatureFlag_passesThroughWithoutLimiting() throws Exception {
        properties.setEnabled(false);
        properties.getLogin().setCapacity(1);
        properties.getLogin().setRefillPerMinute(1);
        RateLimitingFilter filter = filter(fixedIp(FIXED_IP));
        AtomicInteger downstreamInvocations = new AtomicInteger();
        FilterChain chain = countingChain(downstreamInvocations);

        for (int i = 0; i < 10; i++) {
            filter.doFilter(loginRequest("x@example.com"), new MockHttpServletResponse(), chain);
        }

        assertThat(downstreamInvocations.get()).isEqualTo(10);
    }

    @Test
    void differentEmails_sameIp_independentBuckets() throws Exception {
        RateLimitingFilter filter = filter(fixedIp(FIXED_IP));
        AtomicInteger downstreamInvocations = new AtomicInteger();
        FilterChain chain = countingChain(downstreamInvocations);

        for (int i = 0; i < 5; i++) {
            filter.doFilter(loginRequest("a@example.com"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(loginRequest("b@example.com"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
