package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ICourseDomain {
    boolean gradeExists(Integer gradeId);
    boolean parallelExists(Integer parallelId);
    boolean academicYearExists(Integer academicYearId);
    boolean userIsTeacher(UUID userId);
    boolean userIsNonTechnicalTeacher(UUID userId);
    Integer currentAcademicYearId();
    boolean existsByGradeParallelYear(Integer gradeId, Integer parallelId, Integer academicYearId);
    Course create(Integer gradeId, Integer parallelId, Integer academicYearId, UUID homeroomTeacherId);
    Course setHomeroomTeacher(UUID courseId, UUID teacherId);
    Course setActive(UUID courseId, boolean active);
    Optional<Course> findById(UUID id);
    Page<Course> list(Integer academicYearId, Pageable pageable);
    java.util.Optional<Course> homeroomCourseOf(UUID teacherId);
}
