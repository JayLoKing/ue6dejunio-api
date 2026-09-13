package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One place on the cuadro de honor: who holds it, which classroom they come from, and the average
 * that earned it.
 *
 * <p>The average is the very same {@code finalAverage} the libreta prints — a mean of the area
 * averages, rounded at each level. The podium never recomputes it, because a ranking that arrives
 * at its own number would sooner or later disagree with the sheet the school already signed.
 *
 * <p>The course travels with the entry rather than being looked up by the reader. The school's
 * podium mixes classrooms, and a name with no classroom beside it names nobody in a building with
 * two students called the same.
 *
 * @param position      the place on the podium, starting at one.
 * @param gradeName     the year of schooling the student sits in.
 * @param parallelName  the parallel within that year.
 * @param finalAverage  never null: a student with nothing graded holds no place at all.
 */
public record HonorRollEntry(
    int position,
    UUID courseEnrollmentId,
    UUID studentId,
    String fullName,
    UUID courseId,
    String gradeName,
    String parallelName,
    BigDecimal finalAverage
) {}
