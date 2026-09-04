package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the one account that has to exist before anybody can log in.
 *
 * <p>Every value comes from the environment and none of them has a fallback in this file. The
 * Director's e-mail and password used to be defaults here, which meant an install that configured
 * nothing still came up with a working account whose credentials are readable by anyone with the
 * repository — on the one piece of code that runs before there is any user to notice. Unconfigured
 * now creates nothing at all: an install with no Director is obvious the first time somebody tries
 * to sign in, and an install with a publicly known one is not.
 */
@Component
@Profile("!test & !it")
public class BootstrapDirectorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapDirectorRunner.class);

    private final IUserDomain userDomain;
    private final IRoleDomain roleDomain;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String ci;
    private final String names;
    private final String lastNames;

    public BootstrapDirectorRunner(
        IUserDomain userDomain,
        IRoleDomain roleDomain,
        PasswordEncoder passwordEncoder,
        @Value("${app.bootstrap.director.email:}") String email,
        @Value("${app.bootstrap.director.password:}") String password,
        @Value("${app.bootstrap.director.ci:}") String ci,
        @Value("${app.bootstrap.director.names:}") String names,
        @Value("${app.bootstrap.director.last-names:}") String lastNames
    ) {
        this.userDomain = userDomain;
        this.roleDomain = roleDomain;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.ci = ci;
        this.names = names;
        this.lastNames = lastNames;
    }

    @Override
    public void run(String... args) {
        if (isBlank(email) || isBlank(password)) {
            // Half-configured is a mistake, and it is said out loud rather than resolved into an
            // account nobody meant to create.
            log.info("Bootstrap: sin credenciales de Director en el entorno, no se crea ninguna "
                + "cuenta. Configurar BOOTSTRAP_DIRECTOR_EMAIL y BOOTSTRAP_DIRECTOR_PASSWORD.");
            return;
        }
        if (userDomain.findByEmail(email).isPresent()) {
            return;
        }
        Role role = roleDomain.findByName("Director")
            .orElseThrow(() -> new IllegalStateException("Rol Director no existe en la BDD"));

        User user = User.builder()
            .ci(ci)
            .names(names)
            .lastNames(lastNames)
            .email(email)
            .password(passwordEncoder.encode(password))
            .mustChangePassword(true)
            .role(role)
            .active(true)
            .build();
        userDomain.save(user);
        // The e-mail identifies the account and is what the person will type; the password is not
        // logged, and the first login has to replace it anyway.
        log.info("Bootstrap: usuario DIRECTOR creado [{}]. Cambiar contrasena tras primer login.",
            email);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
