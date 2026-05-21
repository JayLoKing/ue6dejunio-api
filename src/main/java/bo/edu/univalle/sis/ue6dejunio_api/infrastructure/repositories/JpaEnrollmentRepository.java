package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaEnrollmentRepository extends JpaRepository<EnrollmentEntity, UUID> {
    boolean existsByStudent_IdAndClassGroup_Id(UUID studentId, UUID classGroupId);
}
