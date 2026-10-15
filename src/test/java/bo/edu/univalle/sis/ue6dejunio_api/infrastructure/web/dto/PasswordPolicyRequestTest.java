package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.PasswordPolicy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every request where a person chooses their own password holds it to the same rule.
 *
 * <p>The first-login change and the menu change both post {@link ChangePasswordRequest}; recovery
 * posts {@link ResetPasswordRequest}. A rule enforced on one and not the other is a weak password
 * reachable through whichever door was left open.
 */
class PasswordPolicyRequestTest {

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

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Ab1!", // too short
                "abcdefg1!", // no uppercase
                "ABCDEFG1!", // no lowercase
                "Abcdefgh!", // no digit
                "Abcdefgh1", // no special character
            })
    void aWeakPassword_isRefusedOnEveryRequestThatSetsOne(String weak) {
        assertThat(messagesOf(validator.validate(new ChangePasswordRequest("current", weak))))
                .contains(PasswordPolicy.MESSAGE);
        assertThat(messagesOf(validator.validate(new ResetPasswordRequest("token", weak))))
                .contains(PasswordPolicy.MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdefg1!", "Mine-Own-Pass-9", "Clave segura 2"})
    void aStrongPassword_passes(String strong) {
        assertThat(validator.validate(new ChangePasswordRequest("current", strong))).isEmpty();
        assertThat(validator.validate(new ResetPasswordRequest("token", strong))).isEmpty();
    }

    private static Set<String> messagesOf(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }
}
