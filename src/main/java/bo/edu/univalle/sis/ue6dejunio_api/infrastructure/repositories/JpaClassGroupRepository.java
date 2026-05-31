package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaClassGroupRepository extends JpaRepository<ClassGroupEntity, UUID> {

    boolean existsByGrade_Id(Integer gradeId);
    boolean existsByParallel_Id(Integer parallelId);
    boolean existsBySubject_Id(java.util.UUID subjectId);

    boolean existsBySubject_IdAndGrade_IdAndParallel_IdAndAcademicYear_Id(
        UUID subjectId, Integer gradeId, Integer parallelId, Integer academicYearId);

    List<ClassGroupEntity> findByTeacher_IdAndAcademicYear_Id(UUID teacherId, Integer academicYearId);

    @Query("""
        SELECT cg.id FROM ClassGroupEntity cg
        WHERE cg.grade.id = :gradeId AND cg.parallel.id = :parallelId
              AND cg.academicYear.id = :academicYearId
        """)
    List<UUID> findIdsByCourse(@Param("gradeId") Integer gradeId,
                               @Param("parallelId") Integer parallelId,
                               @Param("academicYearId") Integer academicYearId);
}
