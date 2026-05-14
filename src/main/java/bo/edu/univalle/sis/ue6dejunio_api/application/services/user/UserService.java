package bo.edu.univalle.sis.ue6dejunio_api.application.services.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UpdateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserService;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UserService implements IUserService {

    private final IUserDomain userDomain;
    private final IRoleDomain roleDomain;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    private final PasswordGenerator passwordGenerator;
    private final IEmailService emailService;

    public UserService(IUserDomain userDomain,
                       IRoleDomain roleDomain,
                       PasswordEncoder passwordEncoder,
                       UsernameGenerator usernameGenerator,
                       PasswordGenerator passwordGenerator,
                       IEmailService emailService) {
        this.userDomain = userDomain;
        this.roleDomain = roleDomain;
        this.passwordEncoder = passwordEncoder;
        this.usernameGenerator = usernameGenerator;
        this.passwordGenerator = passwordGenerator;
        this.emailService = emailService;
    }

    @Override
    public User create(CreateUserCommand command) {
        if (userDomain.existsByEmail(command.email())) {
            throw new DuplicateResourceException("email", command.email());
        }
        if (userDomain.existsByCi(command.ci())) {
            throw new DuplicateResourceException("ci", command.ci());
        }
        Role role = roleDomain.findById(command.roleId())
            .orElseThrow(() -> new ResourceNotFoundException("Rol", command.roleId()));

        String username = usernameGenerator.generateUnique(command.names(), command.lastNames());
        String temporaryPassword = passwordGenerator.generate();

        User user = User.builder()
            .ci(command.ci())
            .names(command.names())
            .lastNames(command.lastNames())
            .phone(command.phone())
            .email(command.email())
            .username(username)
            .password(passwordEncoder.encode(temporaryPassword))
            .mustChangePassword(true)
            .role(role)
            .active(true)
            .build();

        User saved = userDomain.save(user);
        emailService.sendWelcomeCredentials(saved.getEmail(), saved.fullName(), saved.getUsername(), temporaryPassword);
        return saved;
    }

    @Override
    public User update(UUID id, UpdateUserCommand command) {
        User user = userDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        if (command.names() != null) user.setNames(command.names());
        if (command.lastNames() != null) user.setLastNames(command.lastNames());
        if (command.phone() != null) user.setPhone(command.phone());
        if (command.active() != null) user.setActive(command.active());
        if (command.roleId() != null) {
            Role role = roleDomain.findById(command.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Rol", command.roleId()));
            user.setRole(role);
        }
        return userDomain.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UsersList> list(Pageable pageable, @Nullable String search) {
        return userDomain.getUsers(pageable, search);
    }

    @Override
    public void deactivate(UUID id) {
        userDomain.deactivate(id);
    }
}
