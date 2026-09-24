package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        Integer gradeId,
        String gradeName,
        Integer parallelId,
        String parallelName,
        Integer academicYearId,
        Integer year,
        UUID homeroomTeacherId,
        String homeroomTeacherName,
        boolean active) {
    public static CourseResponse from(Course c) {
        return new CourseResponse(
                c.id(),
                c.gradeId(),
                c.gradeName(),
                c.parallelId(),
                c.parallelName(),
                c.academicYearId(),
                c.year(),
                c.homeroomTeacherId(),
                c.homeroomTeacherName(),
                c.active());
    }
}
