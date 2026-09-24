package bo.edu.univalle.sis.ue6dejunio_api.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.PasswordGenerator;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.UserService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UpdateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
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

    @Test
    void create_duplicateEmail_throws() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(true);
        assertThatThrownBy(() -> userService.create(validCommand()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_duplicateCi_throws() {
        when(userDomain.existsByEmail("ana@ue6.bo")).thenReturn(false);
        when(userDomain.existsByCi("1234567")).thenReturn(true);
        assertThatThrownBy(() -> userService.create(validCommand()))
                .isInstanceOf(DuplicateResourceException.class);
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

        UpdateUserCommand toDirector = new UpdateUserCommand(null, null, null, 1, null);

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

        User result = userService.update(id, new UpdateUserCommand(null, null, null, 2, null));

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
}
