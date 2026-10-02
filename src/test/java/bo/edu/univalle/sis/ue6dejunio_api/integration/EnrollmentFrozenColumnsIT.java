package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEnrollmentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** The frozen-result columns of V23: the entity maps them and the CHECK guards the result. */
class EnrollmentFrozenColumnsIT extends AbstractIntegrationTest {

    @Autowired private JpaCourseEnrollmentRepository enrollments;

    private UUID enrollment() {
        UUID teacher = seedUser("Teacher", false);
        UUID course = seedCourse(teacher, "A");
        return seedEnrollment(seedStudent(), course);
    }

    @Test
    void newColumns_defaultToNull_andTheEntityReadsThem() {
        CourseEnrollmentEntity e = enrollments.findById(enrollment()).orElseThrow();

        assertThat(e.getFinalAverage()).isNull();
        assertThat(e.getFinalResult()).isNull();
        assertThat(e.getClosedAt()).isNull();
    }

    @Test
    void frozenValues_roundTripThroughTheEntity() {
        UUID id = enrollment();
        LocalDateTime at = LocalDateTime.of(2026, 10, 2, 12, 0);
        jdbc.update(
                "UPDATE course_enrollments SET final_average = 50.60, final_result = 'APPROVED', "
                        + "closed_at = ? WHERE id_course_enrollment = ?",
                at,
                id);

        CourseEnrollmentEntity e = enrollments.findById(id).orElseThrow();

        assertThat(e.getFinalAverage()).isEqualByComparingTo(new BigDecimal("50.60"));
        assertThat(e.getFinalResult()).isEqualTo("APPROVED");
        assertThat(e.getClosedAt()).isEqualTo(at);
    }

    @Test
    void anUnknownResult_isRejectedByTheDatabase() {
        UUID id = enrollment();

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE course_enrollments SET final_result = 'PENDING' "
                                                + "WHERE id_course_enrollment = ?",
                                        id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
