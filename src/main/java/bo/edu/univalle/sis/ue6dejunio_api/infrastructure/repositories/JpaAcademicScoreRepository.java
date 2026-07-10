package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaAcademicScoreRepository extends JpaRepository<AcademicScoreEntity, UUID> {
    Optional<AcademicScoreEntity> findByCourseEnrollment_IdAndClassGroup_IdAndTrimester(
        UUID courseEnrollmentId, UUID classGroupId, Integer trimester);
    List<AcademicScoreEntity> findByCourseEnrollment_IdOrderByClassGroup_Subject_NameAscTrimesterAsc(
        UUID courseEnrollmentId);
}
