package bo.edu.univalle.sis.ue6dejunio_api.domain.gradebook;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PrintedMark;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** El número entero que la libreta imprime: HALF_UP, igual que la planilla de la escuela. */
class PrintedMarkTest {

    @ParameterizedTest
    @CsvSource({
        "50.49, 50",
        "50.50, 51",
        "50.99, 51",
        "51.00, 51",
        "0.00, 0",
        "99.50, 100",
        "100.00, 100"
    })
    void of_roundsHalfUpToTheWholeNumber(String mark, int expected) {
        assertThat(PrintedMark.of(new BigDecimal(mark))).isEqualByComparingTo(expected + "");
    }

    @Test
    void of_noMark_isNoMark() {
        assertThat(PrintedMark.of(null)).isNull();
    }
}
