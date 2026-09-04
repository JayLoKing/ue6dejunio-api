package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.KnowledgeAreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaKnowledgeAreaRepository extends JpaRepository<KnowledgeAreaEntity, Integer> {

    boolean existsByName(String name);

    /** Zero when the catalogue is empty, so the first area created lands at position one. */
    @Query("SELECT COALESCE(MAX(a.displayOrder), 0) FROM KnowledgeAreaEntity a")
    int maxDisplayOrder();
}
