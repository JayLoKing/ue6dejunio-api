package bo.edu.univalle.sis.ue6dejunio_api.application.services.handover;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.PasswordGenerator;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ForbiddenException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandover;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandoverDetail;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.StartDirectorHandoverCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.Emails;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover.IDirectorHandoverDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover.IDirectorHandoverService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that mints a Director. {@code UserService} still refuses the role on every other
 * path, so the school's head changes hands here or not at all.
 *
 * <p>The failure this class is built around is ending with ZERO active Directors, which locks
 * everyone out of the administration with nobody left to repair it. Three decisions follow from it:
 *
 * <ul>
 *   <li>The outgoing Director keeps full access from the moment the handover opens until the
 *       incoming Director has set a password of their own, so a mail that never arrives costs a
 *       resend, not the school.
 *   <li>The outgoing account is deactivated only after the incoming one is re-read and found to be
 *       an active Director, in the same transaction as the password change that triggers it. If the
 *       incoming account was changed in the meantime the handover stays pending instead.
 *   <li>The mail is sent after the row is stored, so a refused row never leaves a password in an
 *       inbox that was never saved.
 * </ul>
 */
@Service
@Transactional
public class DirectorHandoverService implements IDirectorHandoverService {

    private static final Logger log = LoggerFactory.getLogger(DirectorHandoverService.class);

    private static final String DIRECTOR_ROLE = "Director";

    private final IDirectorHandoverDomain handovers;
    private final IUserDomain userDomain;
    private final IRoleDomain roleDomain;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final IEmailService emailService;
    private final Clock clock;

    public DirectorHandoverService(
            IDirectorHandoverDomain handovers,
            IUserDomain userDomain,
            IRoleDomain roleDomain,
            PasswordEncoder passwordEncoder,
            PasswordGenerator passwordGenerator,
            IEmailService emailService,
            Clock clock) {
        this.handovers = handovers;
        this.userDomain = userDomain;
        this.roleDomain = roleDomain;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Override
    public DirectorHandoverDetail start(StartDirectorHandoverCommand command) {
        User outgoing =
                userDomain
                        .findById(command.outgoingId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Usuario", command.outgoingId()));
        if (!isActiveDirector(outgoing)) {
            throw new ForbiddenException(
                    "Solo un Director activo puede iniciar el cambio de Director");
        }
        if (handovers.findPending().isPresent()) {
            throw new ConflictException(
                    "Ya hay un cambio de Director en curso; cancélalo o espera a que termine");
        }
        String email = command.email() == null ? "" : Emails.normalize(command.email());
        if (email.isEmpty()) {
            throw new ValidationException("El correo del nuevo Director es obligatorio");
        }

        String temporaryPassword = passwordGenerator.generate();
        Optional<User> existing = userDomain.findByEmail(email);
        boolean created = existing.isEmpty();
        Integer previousRoleId = null;
        User incoming;
        if (created) {
            incoming = newDirector(command, email, temporaryPassword);
        } else {
            incoming = existing.get();
            previousRoleId = promotableRoleId(outgoing, incoming);
            promote(incoming, temporaryPassword);
        }
        User savedIncoming = userDomain.save(incoming);

        DirectorHandover row =
                handovers.save(
                        new DirectorHandover(
                                null,
                                outgoing.getId(),
                                savedIncoming.getId(),
                                created,
                                previousRoleId,
                                LocalDateTime.now(clock),
                                null,
                                null));
        // After the row: a refused row (a second handover that raced this one) rolls the whole
        // transaction back, and by then a mail with a password that was never stored is already
        // out of reach.
        emailService.sendWelcomeCredentials(
                savedIncoming.getEmail(), savedIncoming.fullName(), temporaryPassword);
        log.info(
                "Director handover opened: handover={} outgoing={} incoming={} created={}",
                row.id(),
                outgoing.getId(),
                savedIncoming.getId(),
                created);
        return new DirectorHandoverDetail(
                row.id(), outgoing, savedIncoming, created, row.startedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DirectorHandoverDetail> findPending() {
        return handovers.findPending().map(this::detailOf);
    }

    @Override
    public void cancel(UUID callerId) {
        DirectorHandover pending =
                handovers
                        .findPending()
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Cambio de Director en curso", "ninguno"));
        if (!pending.outgoingId().equals(callerId)) {
            throw new ForbiddenException(
                    "Solo el Director saliente puede cancelar el cambio de Director");
        }
        if (pending.incomingCreated()) {
            userDomain.deactivate(pending.incomingId());
        } else {
            restorePreviousRole(pending);
        }
        handovers.save(pending.cancel(LocalDateTime.now(clock)));
        log.info(
                "Director handover cancelled: handover={} outgoing={} incoming={}",
                pending.id(),
                pending.outgoingId(),
                pending.incomingId());
    }

    @Override
    public boolean completeFor(UUID userId) {
        Optional<DirectorHandover> pending = handovers.findPendingByIncoming(userId);
        if (pending.isEmpty()) {
            return false;
        }
        DirectorHandover handover = pending.get();
        User incoming =
                userDomain
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        // The one check that keeps "close the outgoing account" from meaning "close the last
        // Director". The incoming role can be edited, and the account deactivated, between the
        // handover opening and now; if either happened there is nobody to hand over to.
        if (!isActiveDirector(incoming)) {
            log.warn(
                    "Director handover {} not closed: the incoming account is no longer an active"
                            + " Director",
                    handover.id());
            return false;
        }
        handovers.save(handover.close(LocalDateTime.now(clock)));
        userDomain.deactivate(handover.outgoingId());
        log.info(
                "Director handover closed: handover={} outgoing={} incoming={}",
                handover.id(),
                handover.outgoingId(),
                userId);
        return true;
    }

    private User newDirector(StartDirectorHandoverCommand command, String email, String password) {
        requireText(command.ci(), "La cédula de identidad");
        requireText(command.names(), "Los nombres");
        requireText(command.lastNames(), "Los apellidos");
        String ci = command.ci().trim();
        if (userDomain.existsByCi(ci)) {
            throw new DuplicateResourceException("ci", ci);
        }
        return User.builder()
                .ci(ci)
                .names(command.names().trim())
                .lastNames(command.lastNames().trim())
                .phone(command.phone())
                .email(email)
                .password(passwordEncoder.encode(password))
                .mustChangePassword(true)
                .technical(false)
                .role(directorRole())
                .active(true)
                .build();
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    label + " es obligatorio cuando el correo no pertenece a ningún usuario");
        }
    }

    /** The role the user holds now, after checking they can be handed the office at all. */
    private static Integer promotableRoleId(User outgoing, User candidate) {
        if (candidate.getId().equals(outgoing.getId())) {
            throw new ConflictException("No puedes cederte el cargo a ti mismo");
        }
        if (isDirector(candidate)) {
            throw new ConflictException("Ese usuario ya es Director");
        }
        if (!candidate.isActive()) {
            throw new ConflictException(
                    "Ese usuario está dado de baja; actívalo antes de nombrarlo Director");
        }
        return candidate.getRole().id();
    }

    private void promote(User user, String temporaryPassword) {
        user.setRole(directorRole());
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
    }

    private void restorePreviousRole(DirectorHandover handover) {
        Integer previousRoleId = handover.previousRoleId();
        if (previousRoleId == null) {
            // A promoted user always has the role they came from on the row. Without it there is
            // no honest answer to "what were they", and guessing would hand someone a role.
            throw new IllegalStateException(
                    "Handover " + handover.id() + " promoted a user but stored no previous role");
        }
        User incoming =
                userDomain
                        .findById(handover.incomingId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Usuario", handover.incomingId()));
        Role previous =
                roleDomain
                        .findById(previousRoleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Rol", previousRoleId));
        incoming.setRole(previous);
        userDomain.save(incoming);
    }

    private Role directorRole() {
        return roleDomain
                .findByName(DIRECTOR_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Rol", DIRECTOR_ROLE));
    }

    private DirectorHandoverDetail detailOf(DirectorHandover h) {
        User outgoing =
                userDomain
                        .findById(h.outgoingId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Usuario", h.outgoingId()));
        User incoming =
                userDomain
                        .findById(h.incomingId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Usuario", h.incomingId()));
        return new DirectorHandoverDetail(
                h.id(), outgoing, incoming, h.incomingCreated(), h.startedAt());
    }

    private static boolean isDirector(User user) {
        return user.getRole() != null && DIRECTOR_ROLE.equals(user.getRole().name());
    }

    private static boolean isActiveDirector(User user) {
        return user.isActive() && isDirector(user);
    }
}
