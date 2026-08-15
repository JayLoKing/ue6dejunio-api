package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEnrollmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCourseEnrollmentRepository extends JpaRepository<CourseEnrollmentEntity, UUID> {
    boolean existsByStudent_IdAndCourse_Id(UUID studentId, UUID courseId);
    Optional<CourseEnrollmentEntity> findByStudent_IdAndCourse_Id(UUID studentId, UUID courseId);
    @EntityGraph(attributePaths = "student")
    Page<CourseEnrollmentEntity> findByCourse_Id(UUID courseId, Pageable pageable);
    List<CourseEnrollmentEntity> findByStudent_IdAndStatus(UUID studentId, String status);
}
