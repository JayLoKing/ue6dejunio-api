package bo.edu.univalle.sis.ue6dejunio_api.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.auth.AuthService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidResetTokenException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangeEmailCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.UpdateProfileCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover.IDirectorHandoverService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    @Mock private IDirectorHandoverService handoverService;

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
                        handoverService,
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

    /**
     * A password set by the incoming Director is what closes a handover, whichever door it came
     * through. The hook runs after the save, in the same transaction, and only for a password that
     * was actually set.
     */
    @Test
    void resetPassword_validToken_offersTheHandoverTheUser() {
        when(jwtService.validatePasswordResetToken("valid-token")).thenReturn(activeUser.getId());
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.encode("newSecret1")).thenReturn("$encoded$");

        authService.resetPassword("valid-token", "newSecret1");

        InOrder order = inOrder(userDomain, handoverService);
        order.verify(userDomain).save(activeUser);
        order.verify(handoverService).completeFor(activeUser.getId());
    }

    @Test
    void resetPassword_invalidToken_doesNotTouchTheHandover() {
        when(jwtService.validatePasswordResetToken("bad-token"))
                .thenThrow(new InvalidResetTokenException());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "newSecret1"))
                .isInstanceOf(InvalidResetTokenException.class);

        verifyNoInteractions(handoverService);
    }

    @Test
    void changePassword_success_savesThePassword_thenOffersTheHandoverTheUser() {
        activeUser.setMustChangePassword(true);
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("temp", "$hashed$")).thenReturn(true);
        when(passwordEncoder.encode("newSecret1")).thenReturn("$encoded$");

        authService.changePassword(
                new ChangePasswordCommand(activeUser.getId(), "temp", "newSecret1"));

        assertThat(activeUser.getPassword()).isEqualTo("$encoded$");
        assertThat(activeUser.isMustChangePassword()).isFalse();
        InOrder order = inOrder(userDomain, handoverService);
        order.verify(userDomain).save(activeUser);
        order.verify(handoverService).completeFor(activeUser.getId());
    }

    /**
     * A validation error, not invalid credentials: those map to 401, and under a live token the web
     * reads a 401 as an expired session and logs the user out over a typo.
     */
    @Test
    void changePassword_wrongCurrentPassword_isAValidationError_andDoesNotTouchTheHandover() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("bad", "$hashed$")).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                authService.changePassword(
                                        new ChangePasswordCommand(
                                                activeUser.getId(), "bad", "newSecret1")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("La contraseña actual no es correcta");

        verify(userDomain, never()).save(any(User.class));
        verifyNoInteractions(handoverService);
    }

    @Test
    void changePassword_deactivatedUser_doesNotTouchTheHandover() {
        activeUser.setActive(false);
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("temp", "$hashed$")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                authService.changePassword(
                                        new ChangePasswordCommand(
                                                activeUser.getId(), "temp", "newSecret1")))
                .isInstanceOf(UserInactiveException.class);

        verifyNoInteractions(handoverService);
    }

    @Test
    void resetPassword_invalidToken_neverMutatesPassword() {
        when(jwtService.validatePasswordResetToken("bad-token"))
                .thenThrow(new InvalidResetTokenException());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "newSecret1"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userDomain, never()).save(any(User.class));
    }

    @Test
    void updateProfile_changesOnlyNamesLastNamesAndPhone() {
        activeUser.setCi("1234567");
        activeUser.setPhone("70000000");
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        AuthenticatedUser reissued =
                new AuthenticatedUser(
                        activeUser.getId(),
                        activeUser.getEmail(),
                        "Maria Lopez",
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
                .thenReturn(reissued);

        AuthenticatedUser result =
                authService.updateProfile(
                        new UpdateProfileCommand(activeUser.getId(), "Maria", "Lopez", "71111111"));

        assertThat(activeUser.getNames()).isEqualTo("Maria");
        assertThat(activeUser.getLastNames()).isEqualTo("Lopez");
        assertThat(activeUser.getPhone()).isEqualTo("71111111");
        assertThat(activeUser.getEmail()).isEqualTo("director@ue6.bo");
        assertThat(activeUser.getCi()).isEqualTo("1234567");
        assertThat(activeUser.getRole().name()).isEqualTo("DIRECTOR");
        assertThat(result.fullName()).isEqualTo("Maria Lopez");
        verify(userDomain).save(activeUser);
    }

    @Test
    void updateProfile_phoneThatBelongsToSomeoneElse_isDuplicateAndSavesNothing() {
        activeUser.setPhone("70000000");
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(userDomain.existsByPhone("71111111")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                authService.updateProfile(
                                        new UpdateProfileCommand(
                                                activeUser.getId(), "Maria", "Lopez", "71111111")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("El teléfono 71111111 ya está registrado con un usuario del sistema.");

        verify(userDomain, never()).save(any(User.class));
    }

    // The profile form posts the number back on every save; it is this user's own.
    @Test
    void updateProfile_samePhoneAsNow_isNotChecked() {
        activeUser.setPhone("70000000");
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateProfile(
                new UpdateProfileCommand(activeUser.getId(), "Maria", "Lopez", " 70000000 "));

        assertThat(activeUser.getPhone()).isEqualTo("70000000");
        verify(userDomain, never()).existsByPhone(any());
    }

    @Test
    void updateProfile_blankPhone_clearsItWithoutAsking() {
        activeUser.setPhone("70000000");
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateProfile(
                new UpdateProfileCommand(activeUser.getId(), "Maria", "Lopez", ""));

        assertThat(activeUser.getPhone()).isNull();
        verify(userDomain, never()).existsByPhone(any());
    }

    @Test
    void updateProfile_deactivatedUser_throwsUserInactiveAndSavesNothing() {
        activeUser.setActive(false);
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(
                        () ->
                                authService.updateProfile(
                                        new UpdateProfileCommand(
                                                activeUser.getId(), "Maria", "Lopez", null)))
                .isInstanceOf(UserInactiveException.class);

        verify(userDomain, never()).save(any(User.class));
    }

    @Test
    void getProfile_returnsTheCallersOwnRow() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThat(authService.getProfile(activeUser.getId())).isSameAs(activeUser);
    }

    @Test
    void getProfile_deactivatedUser_throwsUserInactive() {
        activeUser.setActive(false);
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.getProfile(activeUser.getId()))
                .isInstanceOf(UserInactiveException.class);
    }

    @Test
    void getProfile_unknownUser_throwsResourceNotFound() {
        UUID ghost = UUID.randomUUID();
        when(userDomain.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getProfile(ghost))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_unknownUser_throwsResourceNotFound() {
        UUID ghost = UUID.randomUUID();
        when(userDomain.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                authService.updateProfile(
                                        new UpdateProfileCommand(ghost, "Maria", "Lopez", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- changeEmail ---

    private AuthenticatedUser sessionFor(String email) {
        return new AuthenticatedUser(
                activeUser.getId(),
                email,
                "Juan Ortuño",
                "DIRECTOR",
                "fresh-jwt",
                Instant.now(),
                Instant.now().plusSeconds(900),
                false,
                null,
                null,
                null,
                null);
    }

    private ChangeEmailCommand changeTo(String newEmail, String currentPassword) {
        return new ChangeEmailCommand(activeUser.getId(), newEmail, currentPassword);
    }

    /**
     * The token carries the address as a claim, so the answer is a fresh session: the caller swaps
     * it in and the header, /me and the next refresh all read the new address.
     */
    @Test
    void changeEmail_rightPassword_movesTheAddressAndReissuesTheSession() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "$hashed$")).thenReturn(true);
        when(userDomain.existsByEmail("nuevo@ue6.bo")).thenReturn(false);
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issueToken(any(User.class), any(), any(), any(), any()))
                .thenReturn(sessionFor("nuevo@ue6.bo"));

        AuthenticatedUser result = authService.changeEmail(changeTo("nuevo@ue6.bo", "secret"));

        assertThat(activeUser.getEmail()).isEqualTo("nuevo@ue6.bo");
        assertThat(activeUser.getPassword()).isEqualTo("$hashed$");
        assertThat(result.email()).isEqualTo("nuevo@ue6.bo");
        verify(userDomain).save(activeUser);
        verify(jwtService).issueToken(activeUser, null, null, null, null);
    }

    /**
     * A 400, not the 401 of a bad login: the web treats any 401 under a live token as an expired
     * session and signs the user out, which is not what a mistyped password deserves.
     */
    @Test
    void changeEmail_wrongPassword_isAValidationErrorAndSavesNothing() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "$hashed$")).thenReturn(false);

        assertThatThrownBy(() -> authService.changeEmail(changeTo("nuevo@ue6.bo", "wrong")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("La contraseña actual no es correcta");

        verify(userDomain, never()).existsByEmail(anyString());
        verify(userDomain, never()).save(any(User.class));
    }

    @Test
    void changeEmail_addressOfAnotherUser_isDuplicateAndSavesNothing() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "$hashed$")).thenReturn(true);
        when(userDomain.existsByEmail("otro@ue6.bo")).thenReturn(true);

        assertThatThrownBy(() -> authService.changeEmail(changeTo("otro@ue6.bo", "secret")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userDomain, never()).save(any(User.class));
    }

    /** Asking to move to the address already held is a mistake worth saying, not a silent pass. */
    @Test
    void changeEmail_sameAddressAsNow_isAValidationError() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "$hashed$")).thenReturn(true);

        assertThatThrownBy(() -> authService.changeEmail(changeTo("director@ue6.bo", "secret")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("El correo nuevo es igual al actual");

        verify(userDomain, never()).save(any(User.class));
    }

    @Test
    void changeEmail_deactivatedUser_throwsUserInactiveAndSavesNothing() {
        activeUser.setActive(false);
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.changeEmail(changeTo("nuevo@ue6.bo", "secret")))
                .isInstanceOf(UserInactiveException.class);

        verify(userDomain, never()).save(any(User.class));
    }

    // --- email normalization ---

    /** Whatever casing or padding is typed, the lookup asks for the stored lowercase address. */
    @Test
    void login_mixedCaseEmail_looksUpTheNormalizedAddress() {
        when(userDomain.findByEmail("director@ue6.bo")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret123", "$hashed$")).thenReturn(true);
        when(jwtService.issueToken(any(User.class), any(), any(), any(), any()))
                .thenReturn(sessionFor("director@ue6.bo"));

        AuthenticatedUser result =
                authService.login(new LoginCommand("  Director@UE6.bo ", "secret123"));

        assertThat(result.email()).isEqualTo("director@ue6.bo");
        verify(userDomain).findByEmail("director@ue6.bo");
    }

    @Test
    void forgotPassword_mixedCaseEmail_reachesTheUser() {
        when(userDomain.findByEmail("director@ue6.bo")).thenReturn(Optional.of(activeUser));
        when(jwtService.issuePasswordResetToken(activeUser)).thenReturn("reset-jwt");

        authService.forgotPassword(" DIRECTOR@ue6.bo");

        verify(emailService)
                .sendPasswordReset(
                        activeUser.getEmail(),
                        activeUser.fullName(),
                        RESET_URL + "?token=reset-jwt");
    }

    @Test
    void changeEmail_mixedCaseAddress_isCheckedAndStoredNormalized() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "$hashed$")).thenReturn(true);
        when(userDomain.existsByEmail("nuevo@ue6.bo")).thenReturn(false);
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issueToken(any(User.class), any(), any(), any(), any()))
                .thenReturn(sessionFor("nuevo@ue6.bo"));

        authService.changeEmail(changeTo(" Nuevo@UE6.bo ", "secret"));

        assertThat(activeUser.getEmail()).isEqualTo("nuevo@ue6.bo");
    }

    @Test
    void changeEmail_mixedCaseAddressOfAnotherUser_isDuplicate() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "$hashed$")).thenReturn(true);
        when(userDomain.existsByEmail("otro@ue6.bo")).thenReturn(true);

        assertThatThrownBy(() -> authService.changeEmail(changeTo("OTRO@ue6.bo", "secret")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userDomain, never()).save(any(User.class));
    }

    /** The current address in capitals is still the current address. */
    @Test
    void changeEmail_currentAddressInOtherCase_isAValidationError() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "$hashed$")).thenReturn(true);

        assertThatThrownBy(() -> authService.changeEmail(changeTo("DIRECTOR@ue6.bo", "secret")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("El correo nuevo es igual al actual");

        verify(userDomain, never()).save(any(User.class));
    }
}
