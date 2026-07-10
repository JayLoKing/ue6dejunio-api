package bo.edu.univalle.sis.ue6dejunio_api.domain.models.course;

import java.util.UUID;

public record UpdateCourseCommand(
    UUID homeroomTeacherId,
    Boolean active
) {}
