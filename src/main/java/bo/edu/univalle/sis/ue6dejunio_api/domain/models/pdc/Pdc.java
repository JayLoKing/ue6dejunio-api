package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One month's curriculum plan for a course.
 *
 * <p>A trimester holds three or four of these, numbered across the year. The plan belongs to the
 * course; which subjects it covers is said by {@link #subjects}, so a homeroom teacher's plan and
 * a specialist's plan are the same shape with a different number of blocks.
 */
@Data
@Builder(toBuilder = true)
public class Pdc {
    private UUID id;
    private UUID courseId;
    private String courseName;
    private String gradeName;
    private String parallelName;
    private UUID homeroomTeacherId;
    private String homeroomTeacherName;

    /** The "Nº 4" of the heading: which plan of the year this is. */
    private Integer planNumber;
    private Integer trimester;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private String status;
    private String reviewObservations;

    private String holisticObjective;
    private String finalProduct;
    private String bibliography;

    /** The plan this one was copied from when the grade's teachers took turns. */
    private UUID sourcePlanId;

    /** The subject blocks, in the order they print. Empty on a listing row, which reads no deeper. */
    @Builder.Default
    private List<PdcSubject> subjects = List.of();

    private UUID createdById;
    private UUID updatedById;
    private String updatedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
