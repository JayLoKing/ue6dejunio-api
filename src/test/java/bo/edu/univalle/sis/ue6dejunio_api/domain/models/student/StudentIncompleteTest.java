package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * A student registered from the school's name-only roster has no RUDE, CI, birth date or gender
 * yet. Any one of the four missing marks the record as incomplete.
 */
class StudentIncompleteTest {

    private static Student.StudentBuilder complete() {
        return Student.builder()
                .rudeCode("RUDE-1")
                .identityCard("CI-1")
                .names("Ana")
                .lastNames("Quispe")
                .birthDate(LocalDate.of(2015, 1, 1))
                .gender("F");
    }

    @Test
    void everyIdentityFieldPresent_isComplete() {
        assertThat(complete().build().isIncomplete()).isFalse();
    }

    @Test
    void anyIdentityFieldMissing_isIncomplete() {
        assertThat(complete().rudeCode(null).build().isIncomplete()).isTrue();
        assertThat(complete().identityCard(null).build().isIncomplete()).isTrue();
        assertThat(complete().birthDate(null).build().isIncomplete()).isTrue();
        assertThat(complete().gender(null).build().isIncomplete()).isTrue();
    }

    @Test
    void staticRule_matchesTheInstanceRule() {
        assertThat(Student.identityIncomplete("R", "C", LocalDate.of(2015, 1, 1), "M")).isFalse();
        assertThat(Student.identityIncomplete(null, null, null, null)).isTrue();
    }
}
