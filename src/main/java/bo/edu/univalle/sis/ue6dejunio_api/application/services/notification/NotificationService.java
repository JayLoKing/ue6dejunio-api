package bo.edu.univalle.sis.ue6dejunio_api.application.services.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationSent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationService implements INotificationService {

    /**
     * Who answers to the Director. The role names are the ones the seed writes and
     * {@code AuthorizationComponent} reads.
     */
    private static final Set<String> MAY_BE_WRITTEN_TO = Set.of("Teacher", "Secretary");

    private final INotificationDomain notificationDomain;
    private final IDomainEventPublisher events;

    public NotificationService(INotificationDomain notificationDomain,
                               IDomainEventPublisher events) {
        this.notificationDomain = notificationDomain;
        this.events = events;
    }

    @Override
    @Transactional
    public Notification send(SendNotificationCommand c) {
        if (!notificationDomain.userExists(c.receiverId())) {
            throw new ResourceNotFoundException("Usuario receptor", c.receiverId());
        }
        // The catalog types are their own heading; CUSTOM is the one the sender has to name. A row
        // with no subject sitting in an inbox is indistinguishable from the one under it.
        boolean hasSubject = c.subject() != null && !c.subject().isBlank();
        if (c.type().requiresSubject() && !hasSubject) {
            throw new ValidationException("Una notificacion personalizada exige un asunto");
        }
        // And the other way, which is the half that drifts if nobody guards it: a catalog type
        // already IS its heading, so a subject beside it leaves the row carrying two of them.
        if (!c.type().requiresSubject() && hasSubject) {
            throw new ValidationException(
                "Solo una notificacion personalizada lleva asunto propio");
        }
        // Only what nobody signed skips this: the listener writes the PDC types with no sender,
        // and those are statements the plan itself made true.
        if (c.senderId() != null) {
            requireAPersonMayWrite(c);
        }
        Notification written = notificationDomain.send(c);
        // Stated after the row exists and only if it does. Whoever listens runs once this commits,
        // so a send that is rolled back nudges nobody towards an inbox that never grew.
        events.publish(new NotificationSent(written.receiverId(), written.id()));
        return written;
    }

    /**
     * What a person is allowed to put in someone's inbox.
     *
     * <p>Two rules, and both are about the inbox being trustworthy. A PDC type posted by hand is a
     * claim nobody checked — the teacher reads that their plan was approved while it sits
     * unreviewed. And the Director supervises teachers and the secretary: another Director is a
     * peer and himself is nobody, so neither is a place the school reaches anyone.
     */
    private void requireAPersonMayWrite(SendNotificationCommand c) {
        if (!c.type().writtenByHand()) {
            throw new ValidationException(
                "Ese aviso lo escribe el sistema cuando el plan cambia de estado");
        }
        String role = notificationDomain.roleNameOf(c.receiverId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario receptor", c.receiverId()));
        if (!MAY_BE_WRITTEN_TO.contains(role)) {
            throw new ValidationException(
                "Una notificacion del Director va a un docente o a la secretaria");
        }
    }

    /**
     * Not read-only, though it reads: handing the rows over is what stamps them delivered.
     *
     * <p>Marked {@code readOnly} this threw "cannot execute UPDATE in a read-only transaction" —
     * a write annotation on the adapter does not lift the restriction, because the adapter joins
     * the transaction this method already opened rather than starting one of its own.
     */
    @Override
    @Transactional
    public PageResult<Notification> inbox(UUID userId, boolean unreadOnly, PageQuery pageQuery) {
        return notificationDomain.listReceived(userId, unreadOnly, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationDomain.unreadCount(userId);
    }

    @Override
    @Transactional
    public Notification markRead(UUID id) {
        // Only the receiver reaches this: @PreAuthorize resolves that before the call.
        Notification n = notificationDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        // Read once and report what the row now is. Fetching it again cost a round trip and, if
        // the receiver deleted it from another tab meanwhile, answered 500 instead of 404. The
        // stamp comes back from the write rather than being read a second time here, or the
        // response would carry an instant the row does not hold.
        LocalDateTime readAt = notificationDomain.markAsRead(id);
        return new Notification(n.id(), n.senderId(), n.senderName(), n.receiverId(),
            n.receiverName(), n.type(), n.subject(), n.message(),
            n.resourceType(), n.resourceId(), n.deliveredAt(), readAt, n.createdAt());
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationDomain.markAllRead(userId);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        notificationDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        notificationDomain.deleteById(id);
    }
}
