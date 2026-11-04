package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The spreadsheet prints one upper-case "SURNAMES NAMES" column and the SIE roster splits it in
 * two, with accents. Both must reduce to the same key, or completing a student from the second
 * creates them again.
 */
class StudentNamesTest {

    @Test
    void caseAccentsAndSpacing_doNotMatter() {
        assertThat(StudentNames.normalizedFullName("  Quispe   Mamaní ", "José  María"))
                .isEqualTo("QUISPE MAMANI JOSE MARIA");
    }

    @Test
    void whereTheSurnamesEndAndTheNamesBegin_doesNotMatter() {
        assertThat(StudentNames.normalizedFullName("QUISPE MAMANI ANA", "MARIA"))
                .isEqualTo(StudentNames.normalizedFullName("Quispe Mamani", "Ana María"));
    }

    @Test
    void enieIsFoldedLikeAnyOtherAccent() {
        assertThat(StudentNames.normalizedFullName("Peña", "Ñusta")).isEqualTo("PENA NUSTA");
    }

    @Test
    void missingParts_areTreatedAsEmpty() {
        assertThat(StudentNames.normalizedFullName(null, "Ana")).isEqualTo("ANA");
        assertThat(StudentNames.normalizedFullName("Quispe", null)).isEqualTo("QUISPE");
    }
}
