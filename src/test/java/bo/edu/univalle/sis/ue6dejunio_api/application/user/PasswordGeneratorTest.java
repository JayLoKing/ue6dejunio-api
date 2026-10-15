package bo.edu.univalle.sis.ue6dejunio_api.application.user;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.PasswordGenerator;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.PasswordPolicy;
import org.junit.jupiter.api.RepeatedTest;

/**
 * The temporary password the school hands out meets the rule the person will be held to.
 *
 * <p>Otherwise the account starts life with a password the change form would refuse, and anything
 * that ever validates the stored one against the policy finds the school's own output wanting.
 * Repeated because the generator is random: one draw passing proves little about the next.
 */
class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @RepeatedTest(200)
    void generate_alwaysMeetsThePasswordPolicy() {
        assertThat(generator.generate()).matches(PasswordPolicy.REGEX);
    }
}
