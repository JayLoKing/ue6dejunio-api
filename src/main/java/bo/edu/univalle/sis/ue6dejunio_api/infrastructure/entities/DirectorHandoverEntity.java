package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A row of {@code director_handovers} (V26). The users are plain ids, not associations: the service
 * loads the two accounts through the user port when it needs them, and a handover row never drags a
 * user's password hash into a query of its own.
 */
@Entity
@Table(name = "director_handovers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectorHandoverEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_handover", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "outgoing_id", nullable = false, updatable = false)
    private UUID outgoingId;

    @Column(name = "incoming_id", nullable = false, updatable = false)
    private UUID incomingId;

    @Column(name = "incoming_created", nullable = false, updatable = false)
    private boolean incomingCreated;

    @Column(name = "previous_role_id", updatable = false)
    private Integer previousRoleId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
