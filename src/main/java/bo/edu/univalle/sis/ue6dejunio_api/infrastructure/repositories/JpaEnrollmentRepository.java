package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EnrollmentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaEnrollmentRepository extends JpaRepository<EnrollmentEntity, UUID> {

    boolean existsByStudent_IdAndClassGroup_Id(UUID studentId, UUID classGroupId);

    Optional<EnrollmentEntity> findByStudent_IdAndClassGroup_Id(UUID studentId, UUID classGroupId);

    @Query("""
        SELECT DISTINCT e.student FROM EnrollmentEntity e
        WHERE e.classGroup.teacher.id = :teacherId
              AND e.classGroup.academicYear.id = :yearId
        ORDER BY e.student.lastNames, e.student.names
        """)
    List<StudentEntity> findDistinctStudentsByTeacher(@Param("teacherId") UUID teacherId,
                                                      @Param("yearId") Integer yearId);
}
