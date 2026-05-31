package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EnrollmentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaEnrollmentRepository extends JpaRepository<EnrollmentEntity, UUID> {

    boolean existsByStudent_IdAndClassGroup_Id(UUID studentId, UUID classGroupId);

    Optional<EnrollmentEntity> findByStudent_IdAndClassGroup_Id(UUID studentId, UUID classGroupId);

    Page<EnrollmentEntity> findByClassGroup_Id(UUID classGroupId, Pageable pageable);

    @Query("""
        SELECT s FROM StudentEntity s
        WHERE EXISTS (
            SELECT 1 FROM EnrollmentEntity e
            WHERE e.student = s
                  AND e.classGroup.teacher.id = :teacherId
                  AND e.classGroup.academicYear.id = :yearId
        )
        """)
    Page<StudentEntity> findDistinctStudentsByTeacherPaged(
        @Param("teacherId") UUID teacherId,
        @Param("yearId") Integer yearId,
        Pageable pageable);

    @Query("""
        SELECT e FROM EnrollmentEntity e
        WHERE e.student.id IN :studentIds
              AND e.classGroup.teacher.id = :teacherId
              AND e.classGroup.academicYear.id = :yearId
        """)
    List<EnrollmentEntity> findEnrollmentsByStudentsAndTeacher(
        @Param("studentIds") Collection<UUID> studentIds,
        @Param("teacherId") UUID teacherId,
        @Param("yearId") Integer yearId);
}
