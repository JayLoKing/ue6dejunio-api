package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.util.UUID;

/**
 * A {@link StudentRisk} that also says which classroom it was filed against.
 *
 * <p>The course is carried on the row for the same reason the two names already are: the school
 * wide list is built a course at a time, and a list of predictions that does not say whose
 * classroom each belongs to can only be grouped by asking the database once per row.
 *
 * @param courseId the course of the class group the prediction was filed against
 * @param risk the prediction itself, with the student and subject names it is read by
 */
public record CourseStudentRisk(UUID courseId, StudentRisk risk) {}
