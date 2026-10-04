package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandover;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover.IDirectorHandoverDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.DirectorHandoverEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaDirectorHandoverRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class DirectorHandoverRepositoryAdapter implements IDirectorHandoverDomain {

    private final JpaDirectorHandoverRepository repo;

    public DirectorHandoverRepositoryAdapter(JpaDirectorHandoverRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<DirectorHandover> findPending() {
        return repo.findByClosedAtIsNullAndCancelledAtIsNull()
                .map(DirectorHandoverRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<DirectorHandover> findPendingByIncoming(UUID incomingId) {
        return repo.findByIncomingIdAndClosedAtIsNullAndCancelledAtIsNull(incomingId)
                .map(DirectorHandoverRepositoryAdapter::toDomain);
    }

    /**
     * Flushed inside the call so the unique index answers here, where it can be told apart from
     * every other integrity failure, and not at commit time, far from the use case that caused it.
     * The index is the only constraint a request can trip on this table: the users are ones the
     * service has just read, and the CHECKs describe states it never builds.
     */
    @Override
    @Transactional
    public DirectorHandover save(DirectorHandover handover) {
        try {
            return toDomain(repo.saveAndFlush(toEntity(handover)));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ya hay un cambio de Director en curso");
        }
    }

    private static DirectorHandover toDomain(DirectorHandoverEntity e) {
        return new DirectorHandover(
                e.getId(),
                e.getOutgoingId(),
                e.getIncomingId(),
                e.isIncomingCreated(),
                e.getPreviousRoleId(),
                e.getStartedAt(),
                e.getClosedAt(),
                e.getCancelledAt());
    }

    private static DirectorHandoverEntity toEntity(DirectorHandover h) {
        return DirectorHandoverEntity.builder()
                .id(h.id())
                .outgoingId(h.outgoingId())
                .incomingId(h.incomingId())
                .incomingCreated(h.incomingCreated())
                .previousRoleId(h.previousRoleId())
                .startedAt(h.startedAt())
                .closedAt(h.closedAt())
                .cancelledAt(h.cancelledAt())
                .build();
    }
}
