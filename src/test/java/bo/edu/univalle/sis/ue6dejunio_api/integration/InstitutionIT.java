package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution.Institution;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.institution.IInstitutionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
        jdbc.update(
                "UPDATE users SET is_active = false WHERE id_role = "
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

    // The libreta and the informe pedagógico both head themselves with more than the district and
    // the school: they print the department, the dependency, the shift and the level of education.
    // None of those vary by course or by student, so they belong to the heading and not to a query.
    @Test
    void carriesEveryFieldTheOfficialDocumentsPrint() {
        Institution heading = institutionService.current();

        assertThat(heading.department()).isNotBlank();
        assertThat(heading.dependency()).isNotBlank();
        assertThat(heading.shift()).isNotBlank();
        assertThat(heading.educationLevel()).isNotBlank();
    }

    /**
     * The accents survive the properties file.
     *
     * <p>{@code .properties} are read as ISO-8859-1, one byte per character, while the file itself
     * is saved as UTF-8 — so a literal {@code ó} written there arrives as the two characters its
     * UTF-8 bytes spell in Latin-1, and the informe pedagógico printed "EducaciÃ³n Primaria" on the
     * school's official form. The values that carry an accent are therefore written as Unicode
     * escapes, which the loader decodes the same way whatever charset it read the file with.
     *
     * <p>Asserting the mojibake is absent and not only that the accent is present: a value
     * half-corrupted would still contain the letter somewhere.
     */
    @Test
    void keepsTheAccentsInTheFieldsThatCarryThem() {
        Institution heading = institutionService.current();

        assertThat(heading.educationLevel()).isEqualTo("Educación Primaria Comunitaria Vocacional");
        assertThat(heading.shift()).isEqualTo("Mañana");
        assertThat(heading.educationLevel()).doesNotContain("Ã");
        assertThat(heading.shift()).doesNotContain("Ã");
    }

    private String fullNameOf(UUID userId) {
        return jdbc.queryForObject(
                "SELECT names || ' ' || last_names FROM users WHERE id_user = ?",
                String.class,
                userId);
    }
}
