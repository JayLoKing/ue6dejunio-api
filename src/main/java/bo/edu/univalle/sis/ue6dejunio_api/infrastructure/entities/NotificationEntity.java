package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class NotificationEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_notification", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id")
    private UserEntity sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id")
    private UserEntity receiver;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    /** Only when the type is CUSTOM: every other type is its own subject. */
    @Column(name = "subject", length = 150)
    private String subject;

    @Column(name = "message", nullable = false)
    private String message;

    /**
     * What the notification is about, held as a loose reference with no foreign key on purpose. The
     * target is a plan today and a risk prediction tomorrow, and the row has to outlive it: a
     * message saying a plan was observed is still a true record of what the Director said after the
     * plan is gone.
     */
    @Column(name = "resource_type", length = 40)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    /** When the receiver's inbox carried it back. */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** The stamp the read flag used to be. Null means unread — there is no boolean beside it. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** Nothing reads this yet. Retention is "archive, never delete" when the volume asks for it. */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
