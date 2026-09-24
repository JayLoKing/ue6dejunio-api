package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IAuthService;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(AuthControllerWebTest.MockBeans.class)
class AuthControllerWebTest {

    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    @MockitoBean private IAuthService authService;

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
    }

    @Test
    void login_returns200WithToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any(LoginCommand.class)))
                .thenReturn(
                        new AuthenticatedUser(
                                id,
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

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                new LoginPayload("director@ue6.bo", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("DIRECTOR"));
    }

    @Test
    void login_invalidEmail_returns400() throws Exception {
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                new LoginPayload("notanemail", "secret123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any(LoginCommand.class)))
                .thenThrow(new InvalidCredentialsException());

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                new LoginPayload("x@ue6.bo", "wrong1"))))
                .andExpect(status().isUnauthorized());
    }

    record LoginPayload(String email, String password) {}
}
