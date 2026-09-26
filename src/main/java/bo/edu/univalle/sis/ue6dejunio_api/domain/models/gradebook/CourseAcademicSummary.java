package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One classroom, reduced to the four numbers the Director's dashboard asks of it.
 *
 * <p>The grain is the course and not the student on purpose: this exists so a school of thirty
 * classrooms can be compared at a glance, and thirty rows of thirty students is the centralizer,
 * which already exists and answers a different question.
 *
 * @param courseId the course these numbers belong to
 * @param gradeName and
 * @param parallelName together they are how the school says a classroom out loud — "Quinto B" — and
 *     they travel with the row so the reader does not have to resolve the id against a second
 *     listing
 * @param students how many enrolments the course holds this gestión
 * @param passed how many of them reach {@link PassingMark#MINIMUM} on their general average
 * @param failed the rest. Stated rather than derived, because a reader adding students minus passed
 *     would get it right only while every student has a computable average
 * @param average the mean of the students' general averages, or null when nobody has been graded
 *     yet — a course with no marks has no average, and zero would say the class failed
 */
public record CourseAcademicSummary(
        UUID courseId,
        String gradeName,
        String parallelName,
        int students,
        int passed,
        int failed,
        BigDecimal average) {}
