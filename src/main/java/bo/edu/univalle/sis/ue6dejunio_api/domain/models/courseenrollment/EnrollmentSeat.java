package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.util.UUID;

/** A seat a student holds, or held, in a course: which course and the status of the enrolment. */
public record EnrollmentSeat(UUID courseId, String status) {}
