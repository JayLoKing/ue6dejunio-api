package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaClassGroupRepository extends JpaRepository<ClassGroupEntity, UUID> {
    boolean existsBySubject_Id(UUID subjectId);
    boolean existsByCourse_IdAndSubject_Id(UUID courseId, UUID subjectId);
    List<ClassGroupEntity> findByCourse_IdOrderBySubject_Name(UUID courseId);
    List<ClassGroupEntity> findByTeacher_IdOrderBySubject_Name(UUID teacherId);

    @Query("SELECT c.id FROM ClassGroupEntity c WHERE c.course.id = :courseId")
    List<UUID> findIdsByCourse(@Param("courseId") UUID courseId);
}
