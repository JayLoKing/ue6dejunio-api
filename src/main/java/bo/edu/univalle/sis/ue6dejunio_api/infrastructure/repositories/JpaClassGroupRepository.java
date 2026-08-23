package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaClassGroupRepository extends JpaRepository<ClassGroupEntity, UUID> {
    boolean existsBySubject_Id(UUID subjectId);
    boolean existsByCourse_IdAndSubject_Id(UUID courseId, UUID subjectId);

    boolean existsByCourse_IdAndTeacher_Id(UUID courseId, UUID teacherId);
    // The to-one associations below are EAGER on the entity, and a derived query does not
    // join-fetch those — Hibernate would resolve subject and teacher with a follow-up select per
    // row. The graph turns the listing back into a single query.
    @EntityGraph(attributePaths = {"course", "course.grade", "course.parallel", "subject", "teacher"})
    List<ClassGroupEntity> findByCourse_IdOrderBySubject_Name(UUID courseId);

    @EntityGraph(attributePaths = {"course", "course.grade", "course.parallel", "subject", "teacher"})
    List<ClassGroupEntity> findByTeacher_IdOrderBySubject_Name(UUID teacherId);

    @Query("SELECT c.id FROM ClassGroupEntity c WHERE c.course.id = :courseId")
    List<UUID> findIdsByCourse(@Param("courseId") UUID courseId);
}
