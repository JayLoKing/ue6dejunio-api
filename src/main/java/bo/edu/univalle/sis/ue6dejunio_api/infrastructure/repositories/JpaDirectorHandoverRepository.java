package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.DirectorHandoverEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDirectorHandoverRepository extends JpaRepository<DirectorHandoverEntity, UUID> {

    /** At most one row matches: the partial unique index of V26 guarantees it. */
    Optional<DirectorHandoverEntity> findByClosedAtIsNullAndCancelledAtIsNull();

    Optional<DirectorHandoverEntity> findByIncomingIdAndClosedAtIsNullAndCancelledAtIsNull(
            UUID incomingId);
}
