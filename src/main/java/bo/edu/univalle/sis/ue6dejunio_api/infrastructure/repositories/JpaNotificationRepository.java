package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.NotificationEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    @EntityGraph(attributePaths = {"sender", "receiver"})
    Page<NotificationEntity> findByReceiver_Id(UUID receiverId, Pageable pageable);

    /** Unread is the absence of a stamp: there is no boolean beside it to disagree with. */
    @EntityGraph(attributePaths = {"sender", "receiver"})
    Page<NotificationEntity> findByReceiver_IdAndReadAtIsNull(UUID receiverId, Pageable pageable);

    long countByReceiver_IdAndReadAtIsNull(UUID receiverId);

    /**
     * Both flags for the same reason as the statement below: a bulk update leaves the rows it
     * changed sitting in the context with their old value, and the next read is served from there.
     * Nothing reads after this one today, which is exactly how it would go unnoticed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE NotificationEntity n SET n.readAt = :now "
                    + "WHERE n.receiver.id = :receiverId AND n.readAt IS NULL")
    int markAllRead(@Param("receiverId") UUID receiverId, @Param("now") LocalDateTime now);

    /**
     * Stamps delivery on what the inbox is about to hand over, in one statement rather than one per
     * row. Only the rows that were never delivered are touched, so a receiver polling every thirty
     * seconds writes once per notification and never again.
     *
     * <p>A bulk update bypasses the persistence context, so both flags are required rather than
     * decorative. Without {@code clearAutomatically} the rows this statement just changed stay in
     * the context carrying their old null, and the next read inside the same transaction is served
     * from there — the stamp appears to have been lost. The entities the caller is holding are
     * detached by the clear, which is harmless: their two associations are eager, so the mapping
     * that follows reads values already in hand and touches no context at all.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE NotificationEntity n SET n.deliveredAt = :now "
                    + "WHERE n.id IN :ids AND n.deliveredAt IS NULL")
    int markDelivered(@Param("ids") Collection<UUID> ids, @Param("now") LocalDateTime now);
}
