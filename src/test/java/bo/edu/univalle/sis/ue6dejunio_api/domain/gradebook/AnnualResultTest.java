package bo.edu.univalle.sis.ue6dejunio_api.domain.gradebook;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * El resultado anual se decide con la nota que la libreta imprime, no con el promedio sin
 * redondear.
 */
class AnnualResultTest {

    @ParameterizedTest
    @CsvSource({
        "50.49, FAILED",
        "50.50, APPROVED",
        "50.99, APPROVED",
        "51.00, APPROVED",
        "0.00, FAILED",
        "100.00, APPROVED"
    })
    void of_decidesByTheMarkTheLibretaPrints(String average, AnnualResult expected) {
        assertThat(AnnualResult.of(new BigDecimal(average))).isEqualTo(expected);
    }

    @Test
    void of_noAverage_isNeitherApprovedNorFailed() {
        assertThat(AnnualResult.of(null)).isNull();
    }
}
