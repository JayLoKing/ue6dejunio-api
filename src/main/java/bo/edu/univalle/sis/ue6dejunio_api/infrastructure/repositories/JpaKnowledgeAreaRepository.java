package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.KnowledgeAreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaKnowledgeAreaRepository extends JpaRepository<KnowledgeAreaEntity, Integer> {
}
