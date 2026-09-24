package bo.edu.univalle.sis.ue6dejunio_api.domain.models.course;

import java.util.UUID;

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
        boolean active) {}
