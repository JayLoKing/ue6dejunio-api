package bo.edu.univalle.sis.ue6dejunio_api.domain.models.course;

import java.util.UUID;

/**
 * {@code active} and {@code homeroomTeacherActive} answer two different questions and are easy to
 * confuse: {@code active} is whether the classroom itself is open (the course row), while {@code
 * homeroomTeacherActive} is whether the person named in {@code homeroomTeacherName} can still sign
 * in. A course with no homeroom teacher reports {@code false} for the latter — there is nobody
 * active to block a reassignment on.
 */
public record Course(
        UUID id,
        Integer gradeId,
        String gradeName,
        Integer parallelId,
        String parallelName,
        Integer academicYearId,
        Integer year,
        UUID homeroomTeacherId,
        String homeroomTeacherName,
        boolean active,
        boolean homeroomTeacherActive) {}
