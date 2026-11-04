package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec A: gender is appended as the LAST field of CourseStudent/CourseStudentResponse, propagated
 * verbatim from the domain model. Backward-compatible: all prior fields unchanged.
 */
class CourseStudentResponseTest {

    @Test
    void from_propagatesGenderAsLastField() {
        CourseStudent domain =
                new CourseStudent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "RUDE-1",
                        "ID-1",
                        "Ana",
                        "Perez",
                        "Effective",
                        "F",
                        null);

        CourseStudentResponse response = CourseStudentResponse.from(domain);

        assertThat(response.gender()).isEqualTo("F");
        assertThat(response.fullName()).isEqualTo("Ana Perez");
        assertThat(response.status()).isEqualTo("Effective");
    }

    @Test
    void from_propagatesMaleGender() {
        CourseStudent domain =
                new CourseStudent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "RUDE-2",
                        "ID-2",
                        "Luis",
                        "Gomez",
                        "Effective",
                        "M",
                        null);

        CourseStudentResponse response = CourseStudentResponse.from(domain);

        assertThat(response.gender()).isEqualTo("M");
    }

    @Test
    void from_flagsTheRecordIncompleteWhileAnyIdentityFieldIsMissing() {
        CourseStudent complete =
                new CourseStudent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "RUDE-3",
                        "ID-3",
                        "Eva",
                        "Rios",
                        "Effective",
                        "F",
                        LocalDate.of(2015, 1, 1));
        CourseStudent nameOnly =
                new CourseStudent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        "Eva",
                        "Rios",
                        "Effective",
                        null,
                        null);

        assertThat(CourseStudentResponse.from(complete).incomplete()).isFalse();
        assertThat(CourseStudentResponse.from(nameOnly).incomplete()).isTrue();
    }
}
