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
    /** "Primaria Comunitaria Vocacional" — the form heads itself with it. */
    private String levelName;
    private UUID homeroomTeacherId;

    /**
     * Who signs the plan as "Maestro/a": the teacher in charge of the course, and only them.
     *
     * <p>Not the author, and not the teachers of the blocks. One teacher of the grade writes the
     * month and hands it to the parallels; the copy belongs to whoever runs the course it lands in,
     * so the name follows the course rather than the writing.
     */
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

    /**
     * How wide the plan is and how much of it answers to a named student — the two numbers a
     * listing row shows so the Director can tell a month's work from a token one before opening it.
     *
     * <p>Counted rather than derived from {@link #subjects}, which a listing row does not carry.
     */
    @Builder.Default
    private int areaCount = 0;
    @Builder.Default
    private int significantAdaptationCount = 0;

    private UUID createdById;
    private UUID updatedById;
    private String updatedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
