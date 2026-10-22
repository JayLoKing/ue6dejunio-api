package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every request that carries an email normalizes it on construction, so {@code @Email} validates
 * the address the user meant and not the whitespace pasted around it.
 */
class EmailRequestNormalizationTest {

    private static final String PADDED = "  Ana.Perez@UE6.bo \t";
    private static final String CLEAN = "ana.perez@ue6.bo";

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
    void login_paddedEmail_isNormalizedAndValid() {
        LoginRequest request = new LoginRequest(PADDED, "secret123");

        assertThat(request.email()).isEqualTo(CLEAN);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void login_passwordKeepsItsSpaces() {
        assertThat(new LoginRequest(CLEAN, " secret 123 ").password()).isEqualTo(" secret 123 ");
    }

    @Test
    void forgotPassword_paddedEmail_isNormalizedAndValid() {
        ForgotPasswordRequest request = new ForgotPasswordRequest(PADDED);

        assertThat(request.email()).isEqualTo(CLEAN);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void changeEmail_paddedEmail_isNormalizedAndValid() {
        ChangeEmailRequest request = new ChangeEmailRequest(PADDED, "secret123");

        assertThat(request.newEmail()).isEqualTo(CLEAN);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createUser_paddedEmail_isNormalizedAndValid() {
        CreateUserRequest request =
                new CreateUserRequest("1234567", "Ana", "Pérez", null, PADDED, 3, false);

        assertThat(request.email()).isEqualTo(CLEAN);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void startDirectorHandover_paddedEmail_isNormalizedAndValid() {
        StartDirectorHandoverRequest request =
                new StartDirectorHandoverRequest(PADDED, null, null, null, null);

        assertThat(request.email()).isEqualTo(CLEAN);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void updateUser_paddedEmail_isNormalizedAndValid() {
        UpdateUserRequest request = new UpdateUserRequest(null, null, null, null, PADDED, null);

        assertThat(request.email()).isEqualTo(CLEAN);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void updateUser_absentEmail_staysAbsent() {
        assertThat(new UpdateUserRequest(null, null, null, null, null, null).email()).isNull();
    }
}
