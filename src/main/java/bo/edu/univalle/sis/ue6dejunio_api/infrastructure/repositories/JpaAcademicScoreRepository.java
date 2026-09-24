package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicScoreEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAcademicScoreRepository extends JpaRepository<AcademicScoreEntity, UUID> {
    Optional<AcademicScoreEntity> findByCourseEnrollment_IdAndClassGroup_IdAndTrimester(
            UUID courseEnrollmentId, UUID classGroupId, Integer trimester);

    List<AcademicScoreEntity>
            findByCourseEnrollment_IdOrderByClassGroup_Subject_NameAscTrimesterAsc(
                    UUID courseEnrollmentId);

    @EntityGraph(attributePaths = {"classGroup", "classGroup.subject"})
    List<AcademicScoreEntity>
            findByCourseEnrollment_IdInOrderByClassGroup_Subject_NameAscTrimesterAsc(
                    Collection<UUID> courseEnrollmentIds);
}
