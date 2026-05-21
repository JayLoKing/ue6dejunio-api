package bo.edu.univalle.sis.ue6dejunio_api.application.user;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.PasswordGenerator;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.UserService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private IUserDomain userDomain;
    @Mock private IRoleDomain roleDomain;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordGenerator passwordGenerator;
    @Mock private IEmailService emailService;
    @InjectMocks private UserService userService;

    private CreateUserCommand validCommand() {
        return new CreateUserCommand("1234567", "Ana", "Quispe", "70000000",
            "ana@ue6.bo", 3);
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
}
