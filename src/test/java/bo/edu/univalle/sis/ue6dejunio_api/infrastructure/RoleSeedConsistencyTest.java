package bo.edu.univalle.sis.ue6dejunio_api.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role names a fresh install gets are the ones the integration schema proves.
 *
 * <p>Role names are matched by string and never normalized — {@code InstitutionRepositoryAdapter}
 * and {@code NotificationRepositoryAdapter} both compare against a literal. So the spelling in the
 * seed is not a cosmetic choice: a database seeded with a different one has no Directors as far as
 * this code can tell, and a plan handed in for review notifies nobody, silently.
 *
 * <p>No integration test can catch that drift, because every integration test seeds itself from
 * {@code schema-it.sql} and therefore always agrees with itself. This test is the only place the
 * script an operator actually runs is held against it.
 */
class RoleSeedConsistencyTest {

    private static final Path PRODUCTION_SCHEMA = Path.of("docs/sql/V1__identity_and_security.sql");
    private static final Path INTEGRATION_SCHEMA = Path.of("src/test/resources/schema-it.sql");

    /** The {@code VALUES ('A'), ('B')} of the one statement that fills the roles table. */
    private static final Pattern ROLE_SEED =
        Pattern.compile("INSERT\\s+INTO\\s+roles\\s*\\(\\s*name\\s*\\)\\s*VALUES(.*?);",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    @Test
    @DisplayName("a fresh install seeds the same role names the integration schema does")
    void productionSeedMatchesIntegrationSeed() throws IOException {
        assertThat(seededRoles(PRODUCTION_SCHEMA))
            .as("role names are compared as literals, so a fresh install that spells them "
                + "differently leaves every role lookup empty")
            .isEqualTo(seededRoles(INTEGRATION_SCHEMA));
    }

    private static List<String> seededRoles(Path schema) throws IOException {
        String sql = Files.readString(schema, StandardCharsets.UTF_8);
        Matcher statement = ROLE_SEED.matcher(sql);
        assertThat(statement.find())
            .as("%s must seed the roles table", schema)
            .isTrue();

        Matcher name = QUOTED.matcher(statement.group(1));
        List<String> names = new ArrayList<>();
        while (name.find()) {
            names.add(name.group(1));
        }
        assertThat(names).as("%s seeds no role name", schema).isNotEmpty();
        return names;
    }
}
