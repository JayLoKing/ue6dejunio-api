package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaCourseRepository extends JpaRepository<CourseEntity, UUID> {
    boolean existsByGrade_IdAndParallel_IdAndAcademicYear_Id(Integer gradeId, Integer parallelId, Integer yearId);
    boolean existsByGrade_Id(Integer gradeId);
    boolean existsByParallel_Id(Integer parallelId);

    @Query("""
        SELECT c FROM CourseEntity c
        WHERE (:yearId IS NULL OR c.academicYear.id = :yearId)
        ORDER BY c.grade.id, c.parallel.id
        """)
    Page<CourseEntity> search(@Param("yearId") Integer yearId, Pageable pageable);

    java.util.Optional<CourseEntity> findFirstByHomeroomTeacher_IdAndActiveTrueOrderByAcademicYear_YearDesc(UUID teacherId);
}
