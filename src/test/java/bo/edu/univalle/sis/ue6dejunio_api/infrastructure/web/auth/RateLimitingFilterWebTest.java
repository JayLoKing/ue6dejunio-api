package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IAuthService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.ClientIpResolver;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.InMemoryBucketStore;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.RateLimitProperties;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.RateLimitStore;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.RateLimitingFilter;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.JwtAuthConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end MockMvc test (no DB, no Docker required): exercises the REAL {@link
 * RateLimitingFilter} wired ahead of {@link AuthController} to prove "Filter Ordering Before
 * Authentication" — a throttled request never invokes {@code AuthService}, and a request within the
 * limit still flows through normally (guards the {@code CachedBodyHttpServletRequest} body re-read
 * against breaking {@code @RequestBody}).
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc // filters enabled (default) so the real RateLimitingFilter bean applies
@ActiveProfiles("test")
@Import(RateLimitingFilterWebTest.MockBeans.class)
class RateLimitingFilterWebTest {

    // Must match app.security.rate-limit.login.capacity in application-test.properties.
    private static final int LOGIN_CAPACITY = 2;

    @Autowired private MockMvc mvc;
    @MockitoBean private IAuthService authService;
    private final ObjectMapper json = new ObjectMapper();

    @TestConfiguration
    static class MockBeans {
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        JwtAuthConverter jwtAuthConverter() {
            return new JwtAuthConverter();
        }

        // Minimal permitAll chain (mirrors production's csrf-disabled, stateless setup) so the
        // real SecurityConfig is not needed in this slice; without it, Boot 4's default fallback
        // security auto-config would apply CSRF protection and reject POST requests with 403.
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        // NOTE: RateLimitProperties carries @ConfigurationProperties, so whatever values are set
        // here are overwritten post-construction by Boot's config binder using the active
        // "test" profile (src/test/resources/application-test.properties). This bean only needs
        // to exist so the real RateLimitingFilter bean can be constructed; LOGIN_CAPACITY below
        // is kept in sync with that file's app.security.rate-limit.login.capacity=2.
        @Bean
        RateLimitProperties rateLimitProperties() {
            return new RateLimitProperties();
        }

        @Bean
        ClientIpResolver clientIpResolver() {
            return request -> "203.0.113.77";
        }

        @Bean
        RateLimitStore rateLimitStore() {
            return new InMemoryBucketStore();
        }

        @Bean
        RateLimitingFilter rateLimitingFilter(
                RateLimitProperties properties, ClientIpResolver ipResolver, RateLimitStore store) {
            return new RateLimitingFilter(properties, ipResolver, store);
        }
    }

    @Test
    void loginAttemptOverLimit_returns429AndNeverInvokesAuthService() throws Exception {
        when(authService.login(any(LoginCommand.class)))
                .thenReturn(
                        new AuthenticatedUser(
                                UUID.randomUUID(),
                                "director@ue6.bo",
                                "Juan Ortuño",
                                "DIRECTOR",
                                "jwt-token",
                                Instant.now(),
                                Instant.now().plusSeconds(900),
                                false,
                                null,
                                null,
                                null,
                                null));

        for (int i = 1; i <= LOGIN_CAPACITY; i++) {
            mvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            json.writeValueAsString(
                                                    new LoginPayload(
                                                            "director@ue6.bo", "secret123"))))
                    .andExpect(status().isOk());
        }
        verify(authService, times(LOGIN_CAPACITY)).login(any(LoginCommand.class));

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                new LoginPayload("director@ue6.bo", "secret123"))))
                .andExpect(status().is(429))
                .andExpect(header().exists("Retry-After"));

        // The (LOGIN_CAPACITY + 1)-th, rejected request must never reach AuthService: the total
        // call count stays at LOGIN_CAPACITY.
        verify(authService, times(LOGIN_CAPACITY)).login(any(LoginCommand.class));
    }

    record LoginPayload(String email, String password) {}
}
