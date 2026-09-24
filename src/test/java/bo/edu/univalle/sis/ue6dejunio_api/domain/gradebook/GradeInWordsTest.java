package bo.edu.univalle.sis.ue6dejunio_api.domain.gradebook;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.GradeInWords;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The libreta's "Literal" column, which spells the annual mark out in words beside the numeral.
 *
 * <p>The school's spreadsheet resolves it with a lookup table of 1..100 typed into hidden columns.
 * The expectations below are that table's own wording, read back out of their file — capitalised
 * words with a lowercase "y", the accents where Spanish puts them. A mark is a number on a document
 * a parent signs, so "Veintidos" without its accent is the wrong word, not a near miss.
 */
class GradeInWordsTest {

    @ParameterizedTest
    @CsvSource({
        "0, Cero",
        "1, Uno",
        "3, Tres",
        "6, Seis",
        "10, Diez",
        "11, Once",
        "15, Quince",
        "20, Veinte"
    })
    void of_theWordsBelowTwentyOne_areSingleWords(int mark, String expected) {
        assertThat(GradeInWords.of(new BigDecimal(mark))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"16, Dieciséis", "22, Veintidós", "23, Veintitrés", "26, Veintiséis"})
    void of_theOnesThatCarryAnAccent_keepIt(int mark, String expected) {
        assertThat(GradeInWords.of(new BigDecimal(mark))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "21, Veintiuno",
        "24, Veinticuatro",
        "25, Veinticinco",
        "27, Veintisiete",
        "28, Veintiocho",
        "29, Veintinueve"
    })
    void of_theTwenties_areOneWord_notThree(int mark, String expected) {
        // Twenty-one is "Veintiuno", never "Veinte y Uno": the twenties contract in Spanish and
        // the thirties onward do not.
        assertThat(GradeInWords.of(new BigDecimal(mark))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "30, Treinta",
        "31, Treinta y Uno",
        "32, Treinta y Dos",
        "40, Cuarenta",
        "49, Cuarenta y Nueve",
        "50, Cincuenta",
        "51, Cincuenta y Uno",
        "55, Cincuenta y Cinco",
        "66, Sesenta y Seis",
        "70, Setenta",
        "79, Setenta y Nueve",
        "80, Ochenta",
        "88, Ochenta y Ocho",
        "90, Noventa",
        "99, Noventa y Nueve"
    })
    void of_fromThirtyUp_joinsTheTensAndTheUnitWithALowercaseY(int mark, String expected) {
        assertThat(GradeInWords.of(new BigDecimal(mark))).isEqualTo(expected);
    }

    @Test
    void of_aHundred_isTheTopOfTheScale() {
        assertThat(GradeInWords.of(new BigDecimal("100"))).isEqualTo("Cien");
    }

    @Test
    void of_aMarkWithDecimals_spellsTheWholeNumberTheLibretaPrints() {
        // The libreta carries one numeral and one literal, and they have to be the same number.
        assertThat(GradeInWords.of(new BigDecimal("43.50"))).isEqualTo("Cuarenta y Cuatro");
        assertThat(GradeInWords.of(new BigDecimal("43.49"))).isEqualTo("Cuarenta y Tres");
    }

    @Test
    void of_nothingGraded_isBlankRatherThanCero() {
        // A student the year has not judged has an empty cell on the sheet. Writing "Cero" there
        // would spell out a mark no teacher gave.
        assertThat(GradeInWords.of(null)).isEmpty();
    }

    @Test
    void of_everyMarkOnTheScale_hasAWord() {
        // The scale runs 0..100 and the sheet has a row for each. A gap would surface as a blank
        // literal beside a real numeral, which reads as a missing mark rather than a missing word.
        for (int mark = 0; mark <= 100; mark++) {
            assertThat(GradeInWords.of(new BigDecimal(mark))).as("mark %d", mark).isNotEmpty();
        }
    }
}
