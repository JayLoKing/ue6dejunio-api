package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IAuthService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.JwtAuthConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@ActiveProfiles("test")
@Import(AuthControllerWebTest.MockBeans.class)
class AuthControllerWebTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private IAuthService authService;

    @TestConfiguration
    static class MockBeans {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean JwtAuthConverter jwtAuthConverter() { return new JwtAuthConverter(); }
    }

    @Test
    void login_returns200WithToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any(LoginCommand.class))).thenReturn(new AuthenticatedUser(
            id, "director@ue6.bo", "Juan Ortuño", "DIRECTOR",
            "jwt-token", Instant.now(), Instant.now().plusSeconds(900), false
        ));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginPayload("director@ue6.bo", "secret123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("jwt-token"))
            .andExpect(jsonPath("$.role").value("DIRECTOR"));
    }

    @Test
    void login_invalidEmail_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginPayload("notanemail", "secret123"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginPayload("x@ue6.bo", "wrong1"))))
            .andExpect(status().isUnauthorized());
    }

    record LoginPayload(String email, String password) {}
}
