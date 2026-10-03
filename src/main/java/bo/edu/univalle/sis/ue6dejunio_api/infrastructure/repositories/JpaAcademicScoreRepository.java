package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicScoreEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAcademicScoreRepository extends JpaRepository<AcademicScoreEntity, UUID> {
    @Query(
            """
        SELECT COUNT(a) > 0 FROM AcademicScoreEntity a
        WHERE a.courseEnrollment.course.academicYear.id = :yearId
          AND a.trimester = :trimester
        """)
    boolean existsInYearAndTrimester(
            @Param("yearId") Integer yearId, @Param("trimester") Integer trimester);

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
