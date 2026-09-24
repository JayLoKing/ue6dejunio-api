package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The libreta's "Literal" column: the annual mark spelled out beside its numeral.
 *
 * <p>The school's spreadsheet resolves this with a lookup table of 1..100 typed into hidden
 * columns. Here it is generated, because a typed table is a hundred chances to misspell a mark on a
 * document a parent signs, and because the row nobody checked is the one that is wrong.
 *
 * <p>Spanish contracts the twenties into one word and separates everything above thirty with a
 * lowercase {@code y}, so the two cases are built differently rather than from one template.
 *
 * <p><b>The unit after the {@code y} is capitalised on purpose: "Treinta y Uno", not "Treinta y
 * uno".</b> Ordinary Spanish orthography wants the lowercase, and this looks like a bug every time
 * somebody reads it. It is not. These words are transcribed from the school's own LIBRETAS
 * workbook, whose lookup column holds "Treinta y Uno", "Cuarenta y Nueve" and "Cincuenta y Uno",
 * and the libreta is required to match the document they already print and sign. Correcting the
 * capitalisation here would make the system's boletín disagree with the school's on every mark
 * ending in a unit. Change it only when the school changes their template.
 */
public final class GradeInWords {

    private static final String[] UNITS = {
        "Cero",
        "Uno",
        "Dos",
        "Tres",
        "Cuatro",
        "Cinco",
        "Seis",
        "Siete",
        "Ocho",
        "Nueve",
        "Diez",
        "Once",
        "Doce",
        "Trece",
        "Catorce",
        "Quince",
        "Dieciséis",
        "Diecisiete",
        "Dieciocho",
        "Diecinueve",
        "Veinte",
        "Veintiuno",
        "Veintidós",
        "Veintitrés",
        "Veinticuatro",
        "Veinticinco",
        "Veintiséis",
        "Veintisiete",
        "Veintiocho",
        "Veintinueve"
    };

    private static final String[] TENS = {
        "", "", "", "Treinta", "Cuarenta", "Cincuenta", "Sesenta", "Setenta", "Ochenta", "Noventa"
    };

    private GradeInWords() {}

    /**
     * The mark in words, or an empty string when there is no mark.
     *
     * <p>Blank and not {@code "Cero"} for an ungraded student: the sheet leaves that cell empty,
     * and spelling out a zero would put a mark on the page that no teacher gave.
     *
     * <p>Decimals are rounded to the whole number the libreta prints as its numeral, so the two
     * columns can never name two different marks.
     *
     * <p>The rounding is HALF_UP, chosen deliberately and not by default: it is what the school's
     * own spreadsheet does with ROUND today, and the libreta is meant to match the document they
     * already sign. It has a consequence worth stating where it happens — an average of 50.6 prints
     * as 51, which is the passing mark, so the libreta can show a pass for a student whose stored
     * average is below it. That was the school's call, not an oversight; changing it means changing
     * what their document says, not fixing a bug.
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
            // impossible mark look like a real one on a signed document. A domain exception and
            // not IllegalArgumentException: the caller printing a libreta gets an answer it can
            // show, rather than the opaque 500 an unmapped runtime exception becomes.
            throw new ValidationException(
                    "La nota " + whole + " está fuera de la escala 0 a 100 de la libreta");
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
