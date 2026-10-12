package bo.edu.univalle.sis.ue6dejunio_api.application.services.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UpdateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UserUpdate;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService implements IUserService {

    /**
     * The role the bootstrap owns, and the one this endpoint refuses to hand out.
     *
     * <p>A Director is what the installation starts with — {@code BootstrapDirectorRunner} writes
     * it from the environment before anybody can sign in. Letting the user form mint another one
     * would mean the account that approves everything can be created by a request, and hiding the
     * option in the web form left that request working for anyone who sends it by hand.
     */
    private static final String DIRECTOR_ROLE = "Director";

    /** The only role for which teaching a technical subject is a meaningful thing to say. */
    private static final String TEACHER_ROLE = "Teacher";

    private final IUserDomain userDomain;
    private final IRoleDomain roleDomain;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final IEmailService emailService;

    public UserService(
            IUserDomain userDomain,
            IRoleDomain roleDomain,
            PasswordEncoder passwordEncoder,
            PasswordGenerator passwordGenerator,
            IEmailService emailService) {
        this.userDomain = userDomain;
        this.roleDomain = roleDomain;
        this.passwordEncoder = passwordEncoder;
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
        Role role =
                roleDomain
                        .findById(command.roleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Rol", command.roleId()));
        rejectDirector(role);

        String temporaryPassword = passwordGenerator.generate();

        User user =
                User.builder()
                        .ci(command.ci())
                        .names(command.names())
                        .lastNames(command.lastNames())
                        .phone(command.phone())
                        .email(command.email())
                        .password(passwordEncoder.encode(temporaryPassword))
                        .mustChangePassword(true)
                        .technical(isTechnicalTeacher(role, command.technical()))
                        .role(role)
                        .active(true)
                        .build();

        User saved = userDomain.save(user);
        emailService.sendWelcomeCredentials(saved.getEmail(), saved.fullName(), temporaryPassword);
        return saved;
    }

    @Override
    public UserUpdate update(UUID id, UpdateUserCommand command) {
        User user =
                userDomain
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        boolean emailChanged = applyEmail(user, command.email());
        if (command.names() != null) user.setNames(command.names());
        if (command.lastNames() != null) user.setLastNames(command.lastNames());
        if (command.phone() != null) user.setPhone(command.phone());
        if (command.active() != null) user.setActive(command.active());
        if (command.roleId() != null) {
            Role role =
                    roleDomain
                            .findById(command.roleId())
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("Rol", command.roleId()));
            rejectDirector(role);
            user.setRole(role);
            // A Secretary who used to teach keeps no trace of it. The flag survived the role change
            // before, and a Secretary marked technical still turned up in the technical-teacher
            // catalogue the Director picks a subject teacher from.
            user.setTechnical(isTechnicalTeacher(role, user.isTechnical()));
        }
        // The welcome mail of someone who never signed in most likely went to the typo being fixed
        // here, so the corrected address gets a working password. Same preconditions as a resend,
        // minus the refusal: a closed or settled account still gets its address corrected.
        boolean resend = emailChanged && user.isActive() && user.isMustChangePassword();
        String temporaryPassword = resend ? issueTemporaryPassword(user) : null;
        User saved = userDomain.save(user);
        if (resend) {
            emailService.sendWelcomeCredentials(
                    saved.getEmail(), saved.fullName(), temporaryPassword);
        }
        return new UserUpdate(saved, resend);
    }

    /**
     * Moves the user to a new address if one was asked for, and says whether it actually moved.
     * Compared and checked for an owner exactly as create does, so an address create would refuse
     * is refused here too.
     */
    private boolean applyEmail(User user, String email) {
        if (email == null || email.equals(user.getEmail())) {
            return false;
        }
        if (email.isBlank()) {
            throw new ValidationException("El correo no puede estar vacío");
        }
        if (userDomain.existsByEmail(email)) {
            throw new DuplicateResourceException("email", email);
        }
        user.setEmail(email);
        return true;
    }

    /** Stores the hash of a fresh temporary password and hands back the raw one for the mail. */
    private String issueTemporaryPassword(User user) {
        String temporaryPassword = passwordGenerator.generate();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        // Already true for every caller, but it is the invariant the mail stands on, so it is
        // stated.
        user.setMustChangePassword(true);
        return temporaryPassword;
    }

    private static void rejectDirector(Role role) {
        if (DIRECTOR_ROLE.equals(role.name())) {
            throw new ValidationException(
                    "El rol Director no puede asignarse desde el registro de usuarios");
        }
    }

    /** The flag as asked for, but only where it means something: everywhere else it is false. */
    private static boolean isTechnicalTeacher(Role role, Boolean requested) {
        return TEACHER_ROLE.equals(role.name()) && requested != null && requested;
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userDomain
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userDomain.findByEmail(email.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UsersList> list(PageQuery pageQuery, String search, UUID excludeUserId) {
        return userDomain.getUsers(pageQuery, search, excludeUserId);
    }

    @Override
    public void deactivate(UUID id) {
        userDomain.deactivate(id);
    }

    @Override
    public void resendCredentials(UUID id) {
        User user =
                userDomain
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (!user.isActive()) {
            throw new ConflictException(
                    "El usuario está dado de baja; actívalo antes de reenviar las credenciales");
        }
        if (!user.isMustChangePassword()) {
            throw new ConflictException(
                    "El usuario ya cambió su contraseña temporal; "
                            + "las credenciales no se pueden reenviar");
        }
        String temporaryPassword = issueTemporaryPassword(user);
        User saved = userDomain.save(user);
        emailService.sendWelcomeCredentials(saved.getEmail(), saved.fullName(), temporaryPassword);
    }

    @Override
    public User activate(UUID id) {
        User user =
                userDomain
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        user.setActive(true);
        return userDomain.save(user);
    }
}
