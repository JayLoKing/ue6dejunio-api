package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAcademicSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One classroom of the Director's table.
 *
 * <p>{@code average} is nullable and stays nullable on the wire: a course nobody has graded yet has
 * no average, and sending a zero would tell the dashboard the whole class failed.
 */
public record CourseAcademicSummaryResponse(
        @JsonProperty("id_course") UUID courseId,
        String gradeName,
        String parallelName,
        int students,
        int passed,
        int failed,
        BigDecimal average) {

    public static CourseAcademicSummaryResponse from(CourseAcademicSummary s) {
        return new CourseAcademicSummaryResponse(
                s.courseId(),
                s.gradeName(),
                s.parallelName(),
                s.students(),
                s.passed(),
                s.failed(),
                s.average());
    }
}
