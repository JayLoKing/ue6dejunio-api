package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaSubjectRepository extends JpaRepository<SubjectEntity, UUID> {
    List<SubjectEntity> findByActiveTrueOrderByName();
    List<SubjectEntity> findByActiveTrueAndTechnicalOrderByName(boolean technical);
    Page<SubjectEntity> findByActiveTrue(Pageable pageable);
}
