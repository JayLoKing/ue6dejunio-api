package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The school's name-only roster carries surnames, names and the course: RUDE, CI, birth date and
 * gender are optional on create, and a blank cell means "not known", never an empty code.
 */
class CreateStudentRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void namesOnly_isValid() {
        CreateStudentRequest request =
                new CreateStudentRequest(null, null, "Ana", "Quispe", null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void blankOptionalFields_becomeNull() {
        CreateStudentRequest request =
                new CreateStudentRequest("  ", "", "Ana", "Quispe", null, " ");

        assertThat(request.rudeCode()).isNull();
        assertThat(request.identityCard()).isNull();
        assertThat(request.gender()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void presentOptionalFields_areTrimmedAndStillValidated() {
        CreateStudentRequest request =
                new CreateStudentRequest(
                        " R-1 ", " C-1 ", "Ana", "Quispe", LocalDate.of(2015, 1, 1), "X");

        assertThat(request.rudeCode()).isEqualTo("R-1");
        assertThat(request.identityCard()).isEqualTo("C-1");
        Set<ConstraintViolation<CreateStudentRequest>> violations = validator.validate(request);
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("gender");
    }

    @Test
    void namesStayRequired() {
        CreateStudentRequest request = new CreateStudentRequest(null, null, " ", null, null, null);

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("names", "lastNames");
    }
}
