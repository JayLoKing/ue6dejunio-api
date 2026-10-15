package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * A phone number belongs to one user at most: the port the services ask, and the index behind it.
 *
 * <p>The query is exercised against Postgres rather than mocked, because the services trust its
 * answer to decide between a 409 and a write; and the index is exercised on its own, because it is
 * what still holds when two requests race past that check.
 */
class UserPhoneUniquenessIT extends AbstractIntegrationTest {

    @Autowired private IUserDomain userDomain;

    private String phoneOf(UUID id) {
        return jdbc.queryForObject("SELECT phone FROM users WHERE id_user = ?", String.class, id);
    }

    @Test
    void existsByPhone_aNumberOnRecord_isFound() {
        String phone = phoneOf(seedUser("Teacher", false));

        assertThat(userDomain.existsByPhone(phone)).isTrue();
    }

    @Test
    void existsByPhone_anUnknownNumber_isNotFound() {
        seedUser("Teacher", false);

        assertThat(userDomain.existsByPhone("69999999")).isFalse();
    }

    // Untrimmed input still finds the stored number: a stray space is not a second phone.
    @Test
    void existsByPhone_paddedInput_findsTheStoredNumber() {
        String phone = phoneOf(seedUser("Teacher", false));

        assertThat(userDomain.existsByPhone("  " + phone + " ")).isTrue();
    }

    // Blank and missing are the absence of a number, and every user without one has the same
    // absence. Answering true here would make the second phoneless account a duplicate.
    @Test
    void existsByPhone_blankOrNull_isNeverADuplicate() {
        UUID teacher = seedUser("Teacher", false);
        jdbc.update("UPDATE users SET phone = '' WHERE id_user = ?", teacher);

        assertThat(userDomain.existsByPhone("")).isFalse();
        assertThat(userDomain.existsByPhone("   ")).isFalse();
        assertThat(userDomain.existsByPhone(null)).isFalse();
    }

    @Test
    void theIndex_refusesASecondUserWithTheSameNumber() {
        UUID first = seedUser("Teacher", false);
        UUID second = seedUser("Secretary", false);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE users SET phone = ? WHERE id_user = ?",
                                        phoneOf(first),
                                        second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theIndex_letsManyUsersGoWithoutANumber() {
        UUID first = seedUser("Teacher", false);
        UUID second = seedUser("Teacher", false);
        UUID third = seedUser("Secretary", false);

        jdbc.update("UPDATE users SET phone = NULL WHERE id_user IN (?, ?)", first, second);
        jdbc.update("UPDATE users SET phone = '  ' WHERE id_user = ?", third);

        assertThat(phoneOf(first)).isNull();
        assertThat(phoneOf(third)).isBlank();
    }
}
