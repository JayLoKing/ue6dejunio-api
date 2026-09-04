package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one account that exists before anyone can log in.
 *
 * <p>What is being pinned here is mostly what the runner refuses to do. It used to carry the
 * Director's e-mail and password as defaults in the source, so a deployment that configured nothing
 * still came up with an account whose credentials are printed in a public repository.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BootstrapDirectorRunnerTest {

    @Mock private IUserDomain userDomain;
    @Mock private IRoleDomain roleDomain;
    @Mock private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "rectora@ue6.bo";
    private static final String PASSWORD = "una-clave-larga";

    private BootstrapDirectorRunner runner(String email, String password) {
        return new BootstrapDirectorRunner(userDomain, roleDomain, passwordEncoder,
            email, password, "1234567", "Elena", "Rojas");
    }

    private void directorRoleExists() {
        when(roleDomain.findByName("Director"))
            .thenReturn(Optional.of(new Role(1, "Director")));
    }

    @Test
    void configured_createsTheDirector() {
        directorRoleExists();
        when(userDomain.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

        runner(EMAIL, PASSWORD).run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().isActive()).isTrue();
    }

    /** The password is never stored as typed, not even the one that seeds the school. */
    @Test
    void configured_storesThePasswordHashed() {
        directorRoleExists();
        when(userDomain.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

        runner(EMAIL, PASSWORD).run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
        assertThat(saved.getValue().getPassword()).isNotEqualTo(PASSWORD);
    }

    /** Whoever seeds the account is not whoever will use it. */
    @Test
    void configured_demandsAPasswordChangeOnFirstLogin() {
        directorRoleExists();
        when(userDomain.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

        runner(EMAIL, PASSWORD).run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(saved.capture());
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
    }

    /**
     * The reason this test exists. With the credentials defaulted in the source, an install that
     * set nothing still came up with a Director whose e-mail and password anyone could read off the
     * repository — and the runner is exactly the code that runs before anybody can log in and
     * notice.
     */
    @Test
    void unconfigured_createsNobody() {
        runner(null, null).run();

        verify(userDomain, never()).save(any());
    }

    @Test
    void blankConfiguration_createsNobody() {
        runner("   ", "   ").run();

        verify(userDomain, never()).save(any());
    }

    /** Half-configured is a mistake, and a mistake must not resolve into an account. */
    @Test
    void emailWithoutPassword_createsNobody() {
        runner(EMAIL, null).run();

        verify(userDomain, never()).save(any());
    }

    @Test
    void passwordWithoutEmail_createsNobody() {
        runner(null, PASSWORD).run();

        verify(userDomain, never()).save(any());
    }

    /** Second start, same account. The seed is not re-applied over a school already running. */
    @Test
    void directorAlreadyThere_createsNobody() {
        when(userDomain.findByEmail(EMAIL))
            .thenReturn(Optional.of(User.builder().email(EMAIL).build()));

        runner(EMAIL, PASSWORD).run();

        verify(userDomain, never()).save(any());
    }

    /** No role, no account: a Director without their role could not be authorized for anything. */
    @Test
    void withoutTheDirectorRole_refusesToStart() {
        when(userDomain.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(roleDomain.findByName("Director")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runner(EMAIL, PASSWORD).run())
            .isInstanceOf(IllegalStateException.class);
    }
}
