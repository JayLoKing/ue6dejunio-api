package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution.Institution;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.institution.IInstitutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The heading every official document prints: the district and the school come from configuration,
 * the Director from the user who holds that role.
 */
class InstitutionIT extends AbstractIntegrationTest {

    @Autowired private IInstitutionService institutionService;

    @Test
    void namesTheDirectorInOffice() {
        UUID director = seedUser("Director", false);

        Institution heading = institutionService.current();

        assertThat(heading.directorName()).isEqualTo(fullNameOf(director));
    }

    // A school that has not registered its Director yet still prints the rest of the heading, with
    // the Director's line left blank the way the paper form leaves it.
    @Test
    void leavesTheDirectorBlankWhenNobodyHoldsTheRole() {
        jdbc.update("UPDATE users SET is_active = false WHERE id_role = "
            + "(SELECT id_role FROM roles WHERE name = 'Director')");

        Institution heading = institutionService.current();

        assertThat(heading.directorName()).isNull();
        assertThat(heading.district()).isNotBlank();
        assertThat(heading.school()).isNotBlank();
    }

    // A Director who left is not the Director. Reading the role without the active flag would keep
    // printing their name on every plan handed in after they were deactivated.
    @Test
    void ignoresADirectorWhoIsNoLongerActive() {
        UUID gone = seedUser("Director", false);
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", gone);
        UUID inOffice = seedUser("Director", false);

        assertThat(institutionService.current().directorName()).isEqualTo(fullNameOf(inOffice));
    }

    private String fullNameOf(UUID userId) {
        return jdbc.queryForObject(
            "SELECT names || ' ' || last_names FROM users WHERE id_user = ?", String.class, userId);
    }
}
