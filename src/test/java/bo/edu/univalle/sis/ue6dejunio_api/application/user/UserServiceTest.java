package bo.edu.univalle.sis.ue6dejunio_api.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.PasswordGenerator;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.UserService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UpdateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UserUpdate;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private IUserDomain userDomain;
    @Mock private IRoleDomain roleDomain;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordGenerator passwordGenerator;
    @Mock private IEmailService emailService;
    @InjectMocks private UserService userService;

    private CreateUserCommand validCommand() {
        return new CreateUserCommand(
                "1234567", "Ana", "Quispe", "70000000", "ana@ue6.bo", 3, false);
    }

    @Test
    void create_success_generatesPasswordAndSendsEmail() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(false);
        when(userDomain.existsByCi("1234567")).thenReturn(false);
        when(roleDomain.findById(3)).thenReturn(Optional.of(new Role(3, "Teacher")));
        when(passwordGenerator.generate()).thenReturn("Gen3rat3d!");
        when(passwordEncoder.encode("Gen3rat3d!")).thenReturn("$hashed$");
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.create(validCommand());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("$hashed$");
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getRole().name()).isEqualTo("Teacher");
        assertThat(result.getEmail()).isEqualTo("ana@ue6.bo");
        verify(emailService).sendWelcomeCredentials("ana@ue6.bo", "Ana Quispe", "Gen3rat3d!");
    }

    /**
     * The message reaches a person, so it is written for one.
     *
     * <p>It used to read {@code Recurso duplicado: email=ana@ue6.bo} — a field name, an equals sign
     * and the value, which is a log line. The web shows this string verbatim in a toast, so the
     * Director creating an account read a debug message and had to work out what went wrong. The
     * address stays in it: pasting a list of new staff, naming the one that collided is the whole
     * point.
     */
    @Test
    void create_duplicateEmail_saysSoInWordsAPersonReads() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(true);
        assertThatThrownBy(() -> userService.create(validCommand()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("El correo ana@ue6.bo ya está registrado con un usuario del sistema.");
    }

    @Test
    void create_duplicateCi_saysSoInWordsAPersonReads() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(false);
        when(userDomain.existsByCi("1234567")).thenReturn(true);
        assertThatThrownBy(() -> userService.create(validCommand()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("El CI 1234567 ya está registrado con un usuario del sistema.");
    }

    @Test
    void create_roleNotFound_throws() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(false);
        when(userDomain.existsByCi("1234567")).thenReturn(false);
        when(roleDomain.findById(3)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.create(validCommand()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_directorRole_throwsValidationAndSavesNothing() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(false);
        when(userDomain.existsByCi("1234567")).thenReturn(false);
        when(roleDomain.findById(1)).thenReturn(Optional.of(new Role(1, "Director")));

        CreateUserCommand asDirector =
                new CreateUserCommand(
                        "1234567", "Ana", "Quispe", "70000000", "ana@ue6.bo", 1, false);

        assertThatThrownBy(() -> userService.create(asDirector))
                .isInstanceOf(ValidationException.class);
        verify(userDomain, never()).save(any(User.class));
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void create_technicalFlagOnNonTeacher_isStoredAsFalse() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(false);
        when(userDomain.existsByCi("1234567")).thenReturn(false);
        when(roleDomain.findById(2)).thenReturn(Optional.of(new Role(2, "Secretary")));
        when(passwordGenerator.generate()).thenReturn("Gen3rat3d!");
        when(passwordEncoder.encode("Gen3rat3d!")).thenReturn("$hashed$");
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(
                new CreateUserCommand(
                        "1234567", "Ana", "Quispe", "70000000", "ana@ue6.bo", 2, true));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(captor.capture());
        assertThat(captor.getValue().isTechnical()).isFalse();
    }

    @Test
    void create_technicalFlagOnTeacher_isStoredAsTrue() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(false);
        when(userDomain.existsByCi("1234567")).thenReturn(false);
        when(roleDomain.findById(3)).thenReturn(Optional.of(new Role(3, "Teacher")));
        when(passwordGenerator.generate()).thenReturn("Gen3rat3d!");
        when(passwordEncoder.encode("Gen3rat3d!")).thenReturn("$hashed$");
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(
                new CreateUserCommand(
                        "1234567", "Ana", "Quispe", "70000000", "ana@ue6.bo", 3, true));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(captor.capture());
        assertThat(captor.getValue().isTechnical()).isTrue();
    }

    @Test
    void update_toDirectorRole_throwsValidationAndSavesNothing() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user =
                User.builder()
                        .id(id)
                        .email("ana@ue6.bo")
                        .role(new Role(3, "Teacher"))
                        .active(true)
                        .build();
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(roleDomain.findById(1)).thenReturn(Optional.of(new Role(1, "Director")));

        UpdateUserCommand toDirector = new UpdateUserCommand(null, null, null, 1, null, null);

        assertThatThrownBy(() -> userService.update(id, toDirector))
                .isInstanceOf(ValidationException.class);
        verify(userDomain, never()).save(any(User.class));
    }

    @Test
    void update_awayFromTeacher_clearsTechnicalFlag() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user =
                User.builder()
                        .id(id)
                        .email("ana@ue6.bo")
                        .technical(true)
                        .role(new Role(3, "Teacher"))
                        .active(true)
                        .build();
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(roleDomain.findById(2)).thenReturn(Optional.of(new Role(2, "Secretary")));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result =
                userService
                        .update(id, new UpdateUserCommand(null, null, null, 2, null, null))
                        .user();

        assertThat(result.isTechnical()).isFalse();
    }

    @Test
    void activate_deactivatedUser_setsActiveTrueAndSaves() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = User.builder().id(id).email("ana@ue6.bo").active(false).build();
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.activate(id);

        assertThat(result.isActive()).isTrue();
        verify(userDomain).save(user);
    }

    @Test
    void activate_alreadyActiveUser_idempotent_staysActiveNoError() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = User.builder().id(id).email("ana@ue6.bo").active(true).build();
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.activate(id);

        assertThat(result.isActive()).isTrue();
    }

    @Test
    void activate_nonexistentUser_throwsResourceNotFound() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.activate(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User pendingUser(java.util.UUID id) {
        return User.builder()
                .id(id)
                .names("Ana")
                .lastNames("Quispe")
                .email("ana@ue6.bo")
                .password("$old$")
                .mustChangePassword(true)
                .active(true)
                .build();
    }

    @Test
    void resendCredentials_pendingUser_storesTheHashAndMailsTheRawPassword() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = pendingUser(id);
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(passwordGenerator.generate()).thenReturn("N3wTemp!");
        when(passwordEncoder.encode("N3wTemp!")).thenReturn("$new$");
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.resendCredentials(id);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$new$");
        assertThat(captor.getValue().isMustChangePassword()).isTrue();
        verify(emailService).sendWelcomeCredentials("ana@ue6.bo", "Ana Quispe", "N3wTemp!");
    }

    @Test
    void resendCredentials_userAlreadyChangedPassword_isConflict() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = pendingUser(id);
        user.setMustChangePassword(false);
        when(userDomain.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.resendCredentials(id))
                .isInstanceOf(ConflictException.class);

        verify(userDomain, never()).save(any(User.class));
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void resendCredentials_inactiveUser_isConflict() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = pendingUser(id);
        user.setActive(false);
        when(userDomain.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.resendCredentials(id))
                .isInstanceOf(ConflictException.class);

        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void findByEmail_trimsTheAddressAndReturnsWhoOwnsIt() {
        User owner = pendingUser(java.util.UUID.randomUUID());
        when(userDomain.findByEmail("ana@ue6.bo")).thenReturn(Optional.of(owner));

        assertThat(userService.findByEmail("  ana@ue6.bo ")).containsSame(owner);
    }

    @Test
    void findByEmail_noOwner_isEmpty() {
        when(userDomain.findByEmail("nadie@ue6.bo")).thenReturn(Optional.empty());

        assertThat(userService.findByEmail("nadie@ue6.bo")).isEmpty();
    }

    private static UpdateUserCommand emailOnly(String email) {
        return new UpdateUserCommand(null, null, null, null, null, email);
    }

    /**
     * The case this exists for: a typo at registration sent the welcome mail nowhere, and the user
     * never signed in. Fixing the address has to put a working password in the right inbox.
     */
    @Test
    void update_emailOfAUserWhoNeverSignedIn_reissuesTheCredentialsToTheNewAddress() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = pendingUser(id);
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(userDomain.existsByEmail("ana.quispe@ue6.bo")).thenReturn(false);
        when(passwordGenerator.generate()).thenReturn("N3wTemp!");
        when(passwordEncoder.encode("N3wTemp!")).thenReturn("$new$");
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserUpdate result = userService.update(id, emailOnly("ana.quispe@ue6.bo"));

        assertThat(result.credentialsResent()).isTrue();
        assertThat(result.user().getEmail()).isEqualTo("ana.quispe@ue6.bo");
        assertThat(result.user().getPassword()).isEqualTo("$new$");
        assertThat(result.user().isMustChangePassword()).isTrue();
        verify(emailService).sendWelcomeCredentials("ana.quispe@ue6.bo", "Ana Quispe", "N3wTemp!");
    }

    /** Someone who already chose a password keeps it; only the address moves. */
    @Test
    void update_emailOfAUserWhoAlreadySignedIn_changesOnlyTheEmail() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = pendingUser(id);
        user.setMustChangePassword(false);
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(userDomain.existsByEmail("ana.quispe@ue6.bo")).thenReturn(false);
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserUpdate result = userService.update(id, emailOnly("ana.quispe@ue6.bo"));

        assertThat(result.credentialsResent()).isFalse();
        assertThat(result.user().getEmail()).isEqualTo("ana.quispe@ue6.bo");
        assertThat(result.user().getPassword()).isEqualTo("$old$");
        verify(passwordGenerator, never()).generate();
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    /** A closed account is not mailed a password: resending refuses it too. */
    @Test
    void update_emailOfAnInactivePendingUser_changesTheEmailButSendsNothing() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = pendingUser(id);
        user.setActive(false);
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(userDomain.existsByEmail("ana.quispe@ue6.bo")).thenReturn(false);
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserUpdate result = userService.update(id, emailOnly("ana.quispe@ue6.bo"));

        assertThat(result.credentialsResent()).isFalse();
        assertThat(result.user().getEmail()).isEqualTo("ana.quispe@ue6.bo");
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void update_emailThatBelongsToSomeoneElse_isDuplicateAndSavesNothing() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userDomain.findById(id)).thenReturn(Optional.of(pendingUser(id)));
        when(userDomain.existsByEmail("otro@ue6.bo")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(id, emailOnly("otro@ue6.bo")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("El correo otro@ue6.bo ya está registrado con un usuario del sistema.");

        verify(userDomain, never()).save(any(User.class));
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    /** The form always posts the address; sending back the current one is not a change. */
    @Test
    void update_sameEmailAsNow_isNotAChangeAndSendsNothing() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userDomain.findById(id)).thenReturn(Optional.of(pendingUser(id)));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserUpdate result = userService.update(id, emailOnly("ana@ue6.bo"));

        assertThat(result.credentialsResent()).isFalse();
        verify(userDomain, never()).existsByEmail(any());
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void update_blankEmail_isRejected() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userDomain.findById(id)).thenReturn(Optional.of(pendingUser(id)));

        assertThatThrownBy(() -> userService.update(id, emailOnly("  ")))
                .isInstanceOf(ValidationException.class);
        verify(userDomain, never()).save(any(User.class));
    }

    @Test
    void resendCredentials_unknownUser_throwsResourceNotFound() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resendCredentials(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- email normalization ---

    /** The web lowercases what it sends; a hand-sent request must not register a second Ana. */
    @Test
    void create_mixedCaseEmail_isCheckedAndStoredNormalized() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(false);
        when(userDomain.existsByCi("1234567")).thenReturn(false);
        when(roleDomain.findById(3)).thenReturn(Optional.of(new Role(3, "Teacher")));
        when(passwordGenerator.generate()).thenReturn("Gen3rat3d!");
        when(passwordEncoder.encode("Gen3rat3d!")).thenReturn("$hashed$");
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(
                new CreateUserCommand(
                        "1234567", "Ana", "Quispe", "70000000", "  ANA@ue6.BO ", 3, false));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ana@ue6.bo");
        verify(emailService).sendWelcomeCredentials("ana@ue6.bo", "Ana Quispe", "Gen3rat3d!");
    }

    @Test
    void create_mixedCaseEmailOfAnExistingUser_isDuplicate() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                userService.create(
                                        new CreateUserCommand(
                                                "1234567",
                                                "Ana",
                                                "Quispe",
                                                "70000000",
                                                "ANA@ue6.bo",
                                                3,
                                                false)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userDomain, never()).save(any(User.class));
    }

    @Test
    void update_mixedCaseEmail_isCheckedAndStoredNormalized() {
        java.util.UUID id = java.util.UUID.randomUUID();
        User user = pendingUser(id);
        user.setMustChangePassword(false);
        when(userDomain.findById(id)).thenReturn(Optional.of(user));
        when(userDomain.existsByEmail("ana.quispe@ue6.bo")).thenReturn(false);
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserUpdate result = userService.update(id, emailOnly(" Ana.Quispe@UE6.bo "));

        assertThat(result.user().getEmail()).isEqualTo("ana.quispe@ue6.bo");
    }

    /** Retyping the current address in capitals is not a change, so nothing is re-mailed. */
    @Test
    void update_currentEmailInOtherCase_isNotAChange() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userDomain.findById(id)).thenReturn(Optional.of(pendingUser(id)));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserUpdate result = userService.update(id, emailOnly(" ANA@ue6.bo"));

        assertThat(result.credentialsResent()).isFalse();
        assertThat(result.user().getEmail()).isEqualTo("ana@ue6.bo");
        verify(userDomain, never()).existsByEmail(any());
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void findByEmail_lowercasesTheAddressBeforeLookingItUp() {
        User owner = pendingUser(java.util.UUID.randomUUID());
        when(userDomain.findByEmail("ana@ue6.bo")).thenReturn(Optional.of(owner));

        assertThat(userService.findByEmail(" Ana@UE6.bo")).containsSame(owner);
    }
}
