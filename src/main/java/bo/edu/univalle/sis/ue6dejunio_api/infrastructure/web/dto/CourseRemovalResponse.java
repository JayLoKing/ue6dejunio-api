package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CourseRemovalOutcome;

/** What DELETE /api/courses/{id} did, so the client can tell the user which of the two it was. */
public record CourseRemovalResponse(CourseRemovalOutcome outcome) {}
