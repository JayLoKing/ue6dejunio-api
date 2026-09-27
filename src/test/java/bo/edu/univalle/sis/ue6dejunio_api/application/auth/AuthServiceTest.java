package bo.edu.univalle.sis.ue6dejunio_api.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.auth.AuthService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidResetTokenException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RESET_URL = "http://localhost:5173/reset-password";

    @Mock private IUserDomain userDomain;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IJwtService jwtService;
    @Mock private ICourseDomain courseDomain;
    @Mock private IEmailService emailService;

    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        authService =
                new AuthService(
                        userDomain,
                        passwordEncoder,
                        jwtService,
                        courseDomain,
                        emailService,
                        RESET_URL);

        activeUser =
                User.builder()
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
        AuthenticatedUser expected =
                new AuthenticatedUser(
                        activeUser.getId(),
                        activeUser.getEmail(),
                        activeUser.fullName(),
                        "DIRECTOR",
                        "jwt",
                        Instant.now(),
                        Instant.now().plusSeconds(900),
                        false,
                        null,
                        null,
                        null,
                        null);
        when(jwtService.issueToken(any(User.class), any(), any(), any(), any()))
                .thenReturn(expected);

        AuthenticatedUser result =
                authService.login(new LoginCommand("director@ue6.bo", "secret123"));

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

        assertThatThrownBy(
                        () -> authService.login(new LoginCommand("director@ue6.bo", "secret123")))
                .isInstanceOf(UserInactiveException.class);
    }

    /**
     * Refresh re-reads the user rather than trusting the token it was handed. That is the whole
     * point: the caller's token is still signed and still inside its window, so the only way to
     * learn that the school closed the account in the meantime is to go and look.
     */
    @Test
    void refresh_activeUser_issuesAFreshToken() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        AuthenticatedUser expected =
                new AuthenticatedUser(
                        activeUser.getId(),
                        activeUser.getEmail(),
                        activeUser.fullName(),
                        "DIRECTOR",
                        "fresh-jwt",
                        Instant.now(),
                        Instant.now().plusSeconds(900),
                        false,
                        null,
                        null,
                        null,
                        null);
        when(jwtService.issueToken(any(User.class), any(), any(), any(), any()))
                .thenReturn(expected);

        AuthenticatedUser result = authService.refresh(activeUser.getId());

        assertThat(result.accessToken()).isEqualTo("fresh-jwt");
    }

    @Test
    void refresh_deactivatedUser_throwsUserInactive() {
        activeUser.setActive(false);
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.refresh(activeUser.getId()))
                .isInstanceOf(UserInactiveException.class);
        verify(jwtService, never()).issueToken(any(), any(), any(), any(), any());
    }

    @Test
    void refresh_deletedUser_throwsResourceNotFound() {
        UUID gone = UUID.randomUUID();
        when(userDomain.findById(gone)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(gone))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * The homeroom claims are recomputed, not carried over from the old token. A course swap moves
     * a teacher between courses, and the claim is what the notebook reads to know which course is
     * theirs: renewing it from the old token would keep pointing at the course they just left.
     */
    @Test
    void refresh_teacher_recomputesTheHomeroomCourse() {
        User teacher =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("nora@ue6.bo")
                        .names("Nora")
                        .lastNames("Arnez")
                        .password("$hashed$")
                        .role(new Role(3, "Teacher"))
                        .active(true)
                        .technical(false)
                        .build();
        UUID courseId = UUID.randomUUID();
        when(userDomain.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        when(courseDomain.homeroomCourseOf(teacher.getId()))
                .thenReturn(
                        Optional.of(
                                new Course(
                                        courseId,
                                        1,
                                        "Segundo",
                                        2,
                                        "B",
                                        2,
                                        2026,
                                        teacher.getId(),
                                        "Nora Arnez",
                                        true,
                                        true)));
        when(jwtService.issueToken(any(User.class), any(), any(), any(), any()))
                .thenReturn(
                        new AuthenticatedUser(
                                teacher.getId(),
                                teacher.getEmail(),
                                teacher.fullName(),
                                "Teacher",
                                "fresh-jwt",
                                Instant.now(),
                                Instant.now().plusSeconds(900),
                                false,
                                "Segundo",
                                "B",
                                courseId,
                                false));

        authService.refresh(teacher.getId());

        verify(jwtService).issueToken(teacher, "Segundo", "B", courseId, false);
    }

    @Test
    void forgotPassword_existingActiveEmail_sendsEmail() {
        when(userDomain.findByEmail("director@ue6.bo")).thenReturn(Optional.of(activeUser));
        when(jwtService.issuePasswordResetToken(activeUser)).thenReturn("reset-jwt");

        authService.forgotPassword("director@ue6.bo");

        verify(emailService)
                .sendPasswordReset(
                        activeUser.getEmail(),
                        activeUser.fullName(),
                        RESET_URL + "?token=reset-jwt");
    }

    @Test
    void forgotPassword_unknownEmail_sendsNoEmailAndDoesNotThrow() {
        when(userDomain.findByEmail("nobody@ue6.bo")).thenReturn(Optional.empty());

        authService.forgotPassword("nobody@ue6.bo");

        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPassword_inactiveEmail_sendsNoEmailAndDoesNotThrow() {
        activeUser.setActive(false);
        when(userDomain.findByEmail("director@ue6.bo")).thenReturn(Optional.of(activeUser));

        authService.forgotPassword("director@ue6.bo");

        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndClearsMustChange() {
        when(jwtService.validatePasswordResetToken("valid-token")).thenReturn(activeUser.getId());
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.encode("newSecret1")).thenReturn("$encoded$");

        authService.resetPassword("valid-token", "newSecret1");

        assertThat(activeUser.getPassword()).isEqualTo("$encoded$");
        assertThat(activeUser.isMustChangePassword()).isFalse();
        verify(userDomain).save(activeUser);
    }

    @Test
    void resetPassword_invalidToken_neverMutatesPassword() {
        when(jwtService.validatePasswordResetToken("bad-token"))
                .thenThrow(new InvalidResetTokenException());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "newSecret1"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userDomain, never()).save(any(User.class));
    }
}
