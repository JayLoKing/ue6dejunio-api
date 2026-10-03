package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

/**
 * One student who held an Effective seat in the previous gestión and has none in the reference one.
 *
 * @param status what the student carries now: "Effective", or "Withdrawn" when they left after the
 *     previous year ended. Still a student who did not come back, so the report keeps them and lets
 *     the screen say why
 * @param lastCourse the course they sat in last year. With two Effective seats that year, the one
 *     with the latest enrolment date; ties break by course id so the answer never flips
 */
public record NotReenrolledStudent(
        UUID studentId,
        String rudeCode,
        String identityCard,
        String fullName,
        String status,
        LastCourse lastCourse) {

    /** The classroom as the school labels it: grade and parallel. */
    public record LastCourse(UUID id, String grade, String parallel) {
        public String name() {
            return grade + " " + parallel;
        }
    }
}
