package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    @EntityGraph(attributePaths = {"sender", "receiver"})
    Page<NotificationEntity> findByReceiver_Id(UUID receiverId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "receiver"})
    Page<NotificationEntity> findByReceiver_IdAndReadFalse(UUID receiverId, Pageable pageable);

    long countByReceiver_IdAndReadFalse(UUID receiverId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true WHERE n.receiver.id = :receiverId AND n.read = false")
    int markAllRead(@Param("receiverId") UUID receiverId);
}
