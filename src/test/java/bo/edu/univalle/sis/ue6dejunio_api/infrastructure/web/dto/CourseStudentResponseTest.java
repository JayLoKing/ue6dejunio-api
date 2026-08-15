package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec A: gender is appended as the LAST field of CourseStudent/CourseStudentResponse,
 * propagated verbatim from the domain model. Backward-compatible: all prior fields unchanged.
 */
class CourseStudentResponseTest {

    @Test
    void from_propagatesGenderAsLastField() {
        CourseStudent domain = new CourseStudent(
            UUID.randomUUID(), UUID.randomUUID(), "RUDE-1", "ID-1",
            "Ana", "Perez", "Effective", "F");

        CourseStudentResponse response = CourseStudentResponse.from(domain);

        assertThat(response.gender()).isEqualTo("F");
        assertThat(response.fullName()).isEqualTo("Ana Perez");
        assertThat(response.status()).isEqualTo("Effective");
    }

    @Test
    void from_propagatesMaleGender() {
        CourseStudent domain = new CourseStudent(
            UUID.randomUUID(), UUID.randomUUID(), "RUDE-2", "ID-2",
            "Luis", "Gomez", "Effective", "M");

        CourseStudentResponse response = CourseStudentResponse.from(domain);

        assertThat(response.gender()).isEqualTo("M");
    }
}
