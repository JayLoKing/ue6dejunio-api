package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.util.UUID;

/**
 * A seat a student holds, or held, in some course of one gestión.
 *
 * @param studentId whose seat it is
 * @param courseId the course it is in
 * @param courseLabel the course as a teacher names it: the grade and the parallel
 * @param status the status of the enrolment, {@code Effective} or {@code Withdrawn}
 */
public record YearSeat(UUID studentId, UUID courseId, String courseLabel, String status) {}
