package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.UsernameGenerator;
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

@Component
@Profile("!test")
public class BootstrapDirectorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapDirectorRunner.class);

    private final IUserDomain userDomain;
    private final IRoleDomain roleDomain;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    private final String email;
    private final String password;
    private final String ci;
    private final String names;
    private final String lastNames;

    public BootstrapDirectorRunner(
        IUserDomain userDomain,
        IRoleDomain roleDomain,
        PasswordEncoder passwordEncoder,
        UsernameGenerator usernameGenerator,
        @Value("${app.bootstrap.director.email:director@ue6.bo}") String email,
        @Value("${app.bootstrap.director.password:Director2026}") String password,
        @Value("${app.bootstrap.director.ci:0000000}") String ci,
        @Value("${app.bootstrap.director.names:Director}") String names,
        @Value("${app.bootstrap.director.last-names:Inicial}") String lastNames
    ) {
        this.userDomain = userDomain;
        this.roleDomain = roleDomain;
        this.passwordEncoder = passwordEncoder;
        this.usernameGenerator = usernameGenerator;
        this.email = email;
        this.password = password;
        this.ci = ci;
        this.names = names;
        this.lastNames = lastNames;
    }

    @Override
    public void run(String... args) {
        if (userDomain.findByEmail(email).isPresent()) {
            return;
        }
        Role role = roleDomain.findByName("Director")
            .orElseThrow(() -> new IllegalStateException("Rol Director no existe en la BDD"));

        String username = usernameGenerator.generateUnique(names, lastNames);
        User user = User.builder()
            .ci(ci)
            .names(names)
            .lastNames(lastNames)
            .email(email)
            .username(username)
            .password(passwordEncoder.encode(password))
            .mustChangePassword(true)
            .role(role)
            .active(true)
            .build();
        userDomain.save(user);
        log.info("Bootstrap: usuario DIRECTOR creado [{}] username [{}]. Cambiar contraseña tras primer login.", email, username);
    }
}
