package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.NotificationEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaNotificationRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class NotificationRepositoryAdapter implements INotificationDomain {

    private final JpaNotificationRepository notificationRepo;
    private final JpaUserRepository userRepo;
    private final JpaCourseEnrollmentRepository enrollmentRepo;

    public NotificationRepositoryAdapter(JpaNotificationRepository notificationRepo,
                                         JpaUserRepository userRepo,
                                         JpaCourseEnrollmentRepository enrollmentRepo) {
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
        this.enrollmentRepo = enrollmentRepo;
    }

    /**
     * Now, at the resolution the column can hold.
     *
     * <p>{@code LocalDateTime.now()} carries nanoseconds and a Postgres {@code timestamp} keeps
     * microseconds, so the value handed back in a response was never quite the value stored: the
     * write reported one instant and every later read of the same row reported another. Truncating
     * before the write is what makes the answer and the row agree.
     */
    private static LocalDateTime nowAsStored() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** The role name as the seed writes it, and as {@code AuthorizationComponent} reads it. */
    private static final String DIRECTOR_ROLE = "Director";

    @Override
    public boolean userExists(UUID userId) {
        return userRepo.existsById(userId);
    }

    @Override
    public Optional<String> roleNameOf(UUID userId) {
        return userRepo.findById(userId)
            .map(UserEntity::getRole)
            .map(role -> role.getName());
    }

    @Override
    public List<UUID> activeDirectorIds() {
        return userRepo.findByRole_NameAndActiveTrueOrderByLastNames(DIRECTOR_ROLE).stream()
            .map(UserEntity::getId)
            .toList();
    }

    /**
     * Two queries rather than one: JPQL has no union that reads as well as this, and the two
     * questions are genuinely different — who runs the course, and who runs a subject inside it.
     * A set because a homeroom teacher who also teaches a subject there is one person, and would
     * otherwise get the same notice twice.
     */
    @Override
    public List<UUID> teacherIdsResponsibleForStudent(UUID studentId) {
        Set<UUID> teachers = new LinkedHashSet<>(
            enrollmentRepo.findHomeroomTeacherIdsOfStudent(studentId));
        teachers.addAll(enrollmentRepo.findClassGroupTeacherIdsOfStudent(studentId));
        return List.copyOf(teachers);
    }

    @Override
    @Transactional
    public Notification send(SendNotificationCommand c) {
        UserEntity receiver = userRepo.findById(c.receiverId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario receptor", c.receiverId()));
        NotificationEntity e = new NotificationEntity();
        // Null when the system wrote it — a state change, or later the predictive model. Nobody
        // signs those, and inventing a sender would put a person's name on a machine's message.
        if (c.senderId() != null) {
            e.setSender(userRepo.getReferenceById(c.senderId()));
        }
        e.setReceiver(receiver);
        e.setType(c.type());
        e.setSubject(c.subject());
        e.setMessage(c.message());
        e.setResourceType(c.resourceType());
        e.setResourceId(c.resourceId());
        e.setCreatedAt(nowAsStored());
        return toDomain(notificationRepo.save(e));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return notificationRepo.findById(id).map(this::toDomain);
    }

    /**
     * Reading the inbox is what stamps delivery, and that is why this write sits on a read.
     *
     * <p>Nothing on the receiver's side reports back, so the only moment the server can honestly
     * say a notification arrived is the moment it hands the bytes over. The statement touches only
     * rows never delivered, so a receiver polling every thirty seconds writes once per
     * notification and never again.
     */
    @Override
    @Transactional
    public PageResult<Notification> listReceived(UUID receiverId, boolean unreadOnly,
                                                 PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        Page<NotificationEntity> page = unreadOnly
            ? notificationRepo.findByReceiver_IdAndReadAtIsNull(receiverId, pageable)
            : notificationRepo.findByReceiver_Id(receiverId, pageable);

        LocalDateTime now = nowAsStored();
        Set<UUID> justDelivered = page.getContent().stream()
            .filter(e -> e.getDeliveredAt() == null)
            .map(NotificationEntity::getId)
            .collect(Collectors.toSet());
        if (!justDelivered.isEmpty()) {
            notificationRepo.markDelivered(justDelivered, now);
        }
        // The entities in hand were loaded before that statement and still carry the old null. The
        // stamp goes on the answer rather than on them: writing it into a managed entity makes it
        // dirty, and Hibernate would flush one UPDATE per row on top of the single one just run.
        return SpringPaging.toPageResult(page.map(e -> delivered(e, justDelivered, now)));
    }

    /** The row as the receiver now has it, without touching what the persistence context holds. */
    private Notification delivered(NotificationEntity e, Set<UUID> justDelivered,
                                   LocalDateTime now) {
        Notification n = toDomain(e);
        if (!justDelivered.contains(e.getId())) {
            return n;
        }
        return new Notification(n.id(), n.senderId(), n.senderName(), n.receiverId(),
            n.receiverName(), n.type(), n.subject(), n.message(), n.resourceType(),
            n.resourceId(), now, n.readAt(), n.createdAt());
    }

    @Override
    public long unreadCount(UUID receiverId) {
        return notificationRepo.countByReceiver_IdAndReadAtIsNull(receiverId);
    }

    @Override
    @Transactional
    public LocalDateTime markAsRead(UUID id) {
        NotificationEntity e = notificationRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        // Reading it a second time does not move the stamp: what is recorded is when the receiver
        // first saw it, not when they last opened it.
        if (e.getReadAt() == null) {
            // No save: the entity is managed by the transaction this method opened, so the change
            // flushes on its own. Calling save would be a round trip that changes nothing.
            e.setReadAt(nowAsStored());
        }
        // Handed back rather than left for the caller to guess: a second clock reading up there
        // would answer with an instant the row does not hold.
        return e.getReadAt();
    }

    @Override
    @Transactional
    public int markAllRead(UUID receiverId) {
        return notificationRepo.markAllRead(receiverId, nowAsStored());
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        notificationRepo.deleteById(id);
    }

    private Notification toDomain(NotificationEntity e) {
        UserEntity s = e.getSender();
        UserEntity r = e.getReceiver();
        return new Notification(
            e.getId(),
            s != null ? s.getId() : null,
            s != null ? s.getNames() + " " + s.getLastNames() : null,
            r != null ? r.getId() : null,
            r != null ? r.getNames() + " " + r.getLastNames() : null,
            e.getType(), e.getSubject(), e.getMessage(),
            e.getResourceType(), e.getResourceId(),
            e.getDeliveredAt(), e.getReadAt(), e.getCreatedAt()
        );
    }
}
