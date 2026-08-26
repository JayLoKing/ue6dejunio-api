package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.NotificationEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaNotificationRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class NotificationRepositoryAdapter implements INotificationDomain {

    private final JpaNotificationRepository notificationRepo;
    private final JpaUserRepository userRepo;

    public NotificationRepositoryAdapter(JpaNotificationRepository notificationRepo,
                                         JpaUserRepository userRepo) {
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
    }

    @Override
    public boolean userExists(UUID userId) {
        return userRepo.existsById(userId);
    }

    @Override
    @Transactional
    public Notification send(UUID senderId, UUID receiverId, String message) {
        UserEntity sender = userRepo.getReferenceById(senderId);
        UserEntity receiver = userRepo.findById(receiverId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario receptor", receiverId));
        NotificationEntity e = new NotificationEntity();
        e.setSender(sender);
        e.setReceiver(receiver);
        e.setMessage(message);
        e.setRead(false);
        e.setCreatedAt(LocalDateTime.now());
        return toDomain(notificationRepo.save(e));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return notificationRepo.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Notification> listReceived(UUID receiverId, boolean unreadOnly,
                                                 PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        Page<NotificationEntity> page = unreadOnly
            ? notificationRepo.findByReceiver_IdAndReadFalse(receiverId, pageable)
            : notificationRepo.findByReceiver_Id(receiverId, pageable);
        return SpringPaging.toPageResult(page.map(this::toDomain));
    }

    @Override
    public long unreadCount(UUID receiverId) {
        return notificationRepo.countByReceiver_IdAndReadFalse(receiverId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID id) {
        NotificationEntity e = notificationRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        e.setRead(true);
        notificationRepo.save(e);
    }

    @Override
    @Transactional
    public int markAllRead(UUID receiverId) {
        return notificationRepo.markAllRead(receiverId);
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
            e.getMessage(), e.isRead(), e.getCreatedAt()
        );
    }
}
