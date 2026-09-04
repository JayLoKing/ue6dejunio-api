package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaSubjectRepository extends JpaRepository<SubjectEntity, UUID> {
    // area is EAGER on the entity and a derived query does not join-fetch it, so without the graph
    // Hibernate resolves it with one follow-up select per row.
    @EntityGraph(attributePaths = {"area"})
    List<SubjectEntity> findByActiveTrueOrderByName();

    @EntityGraph(attributePaths = {"area"})
    List<SubjectEntity> findByActiveTrueAndTechnicalOrderByName(boolean technical);

    @EntityGraph(attributePaths = {"area"})
    Page<SubjectEntity> findByActiveTrue(Pageable pageable);

    /**
     * Whether the area is still spoken for. Inactive subjects count: deactivating one leaves the
     * row and its foreign key in place, so the area is not free to be deleted either way.
     */
    boolean existsByArea_Id(Integer areaId);
}
