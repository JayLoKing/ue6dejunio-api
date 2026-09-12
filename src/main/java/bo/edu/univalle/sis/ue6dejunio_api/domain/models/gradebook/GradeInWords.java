package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The libreta's "Literal" column: the annual mark spelled out beside its numeral.
 *
 * <p>The school's spreadsheet resolves this with a lookup table of 1..100 typed into hidden
 * columns. Here it is generated, because a typed table is a hundred chances to misspell a mark on
 * a document a parent signs, and because the row nobody checked is the one that is wrong.
 *
 * <p>Spanish contracts the twenties into one word and separates everything above thirty with a
 * lowercase {@code y}, so the two cases are built differently rather than from one template.
 */
public final class GradeInWords {

    private static final String[] UNITS = {
        "Cero", "Uno", "Dos", "Tres", "Cuatro", "Cinco", "Seis", "Siete", "Ocho", "Nueve",
        "Diez", "Once", "Doce", "Trece", "Catorce", "Quince",
        "Dieciséis", "Diecisiete", "Dieciocho", "Diecinueve", "Veinte",
        "Veintiuno", "Veintidós", "Veintitrés", "Veinticuatro", "Veinticinco",
        "Veintiséis", "Veintisiete", "Veintiocho", "Veintinueve"
    };

    private static final String[] TENS = {
        "", "", "", "Treinta", "Cuarenta", "Cincuenta", "Sesenta", "Setenta", "Ochenta", "Noventa"
    };

    private GradeInWords() {
    }

    /**
     * The mark in words, or an empty string when there is no mark.
     *
     * <p>Blank and not {@code "Cero"} for an ungraded student: the sheet leaves that cell empty,
     * and spelling out a zero would put a mark on the page that no teacher gave.
     *
     * <p>Decimals are rounded to the whole number the libreta prints as its numeral, so the two
     * columns can never name two different marks.
     *
     * @param mark the annual average, or null when nothing is graded.
     */
    public static String of(BigDecimal mark) {
        if (mark == null) {
            return "";
        }
        int whole = mark.setScale(0, RoundingMode.HALF_UP).intValueExact();
        if (whole < 0 || whole > 100) {
            // Outside the scale there is no right word, and inventing one would make an
            // impossible mark look like a real one on a signed document.
            throw new IllegalArgumentException(
                "A mark outside 0..100 has no word on the libreta's scale: " + whole);
        }
        if (whole == 100) {
            return "Cien";
        }
        if (whole < UNITS.length) {
            return UNITS[whole];
        }
        String tens = TENS[whole / 10];
        int unit = whole % 10;
        return unit == 0 ? tens : tens + " y " + UNITS[unit];
    }
}
