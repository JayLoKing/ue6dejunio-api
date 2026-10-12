package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailsTest {

    @Test
    void normalize_trimsAndLowercases() {
        assertThat(Emails.normalize("  Ana@X.com ")).isEqualTo("ana@x.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@x.com", "ANA@X.COM", "\tAna@x.Com\n", " aNa@X.cOm"})
    void normalize_everySpellingOfOneAddressLandsOnTheSameValue(String raw) {
        assertThat(Emails.normalize(raw)).isEqualTo("ana@x.com");
    }

    @Test
    void normalize_isIdempotent() {
        String once = Emails.normalize(" Ana@X.com");

        assertThat(Emails.normalize(once)).isEqualTo(once);
    }

    @Test
    void normalize_null_staysNull() {
        assertThat(Emails.normalize(null)).isNull();
    }

    @Test
    void normalize_blank_becomesEmpty() {
        assertThat(Emails.normalize("   ")).isEmpty();
    }

    /** A Turkish default locale lowercases 'I' to a dotless 'ı', which would split one account. */
    @Test
    void normalize_doesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(Emails.normalize("IVAN@X.COM")).isEqualTo("ivan@x.com");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
