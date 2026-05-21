package bo.edu.univalle.sis.ue6dejunio_api.application.auth;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.auth.AuthService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private IUserDomain userDomain;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IJwtService jwtService;
    @Mock private IClassGroupDomain classGroupDomain;

    @InjectMocks private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
            .id(UUID.randomUUID())
            .email("director@ue6.bo")
            .names("Juan")
            .lastNames("Ortuño")
            .password("$hashed$")
            .role(new Role(1, "DIRECTOR"))

            .active(true)
            .build();
    }

    @Test
    void login_success_returnsTokenForActiveUser() {
        when(userDomain.findByEmail("director@ue6.bo")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret123", "$hashed$")).thenReturn(true);
        AuthenticatedUser expected = new AuthenticatedUser(
            activeUser.getId(), activeUser.getEmail(), activeUser.fullName(),
            "DIRECTOR", "jwt", Instant.now(), Instant.now().plusSeconds(900), false, null, null
        );
        when(jwtService.issueToken(any(User.class), any(), any())).thenReturn(expected);

        AuthenticatedUser result = authService.login(new LoginCommand("director@ue6.bo", "secret123"));

        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.role()).isEqualTo("DIRECTOR");
    }

    @Test
    void login_userNotFound_throwsInvalidCredentials() {
        when(userDomain.findByEmail("missing@ue6.bo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("missing@ue6.bo", "x")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        when(userDomain.findByEmail("director@ue6.bo")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("bad", "$hashed$")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("director@ue6.bo", "bad")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_inactiveUser_throwsUserInactive() {
        activeUser.setActive(false);
        when(userDomain.findByEmail("director@ue6.bo")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret123", "$hashed$")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginCommand("director@ue6.bo", "secret123")))
            .isInstanceOf(UserInactiveException.class);
    }
}
