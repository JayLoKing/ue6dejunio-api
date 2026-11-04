package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The key two rosters agree on when neither carries a RUDE or CI.
 *
 * <p>The school's spreadsheet prints one upper-case column, surnames first, with no accents; the
 * SIE roster prints surnames and names apart, accented. Both reduce to the same key here: surnames
 * then names, upper case, accents and the tilde of the eñe removed, runs of spaces collapsed. Where
 * one source ends the surnames and starts the names does not matter, because the key is the whole
 * name joined.
 */
public final class StudentNames {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private StudentNames() {}

    public static String normalizedFullName(String lastNames, String names) {
        String joined = (lastNames == null ? "" : lastNames) + " " + (names == null ? "" : names);
        String withoutMarks =
                MARKS.matcher(Normalizer.normalize(joined, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(withoutMarks.toUpperCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
