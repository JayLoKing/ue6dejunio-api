package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * "Exactly one academic year is active" is a database guarantee, not a convention the application
 * keeps: the partial unique index is what makes two active rows impossible no matter which code
 * path writes them. This drives the constraint with plain SQL because the constraint is the
 * subject; the application never gets a chance to be polite first.
 */
class AcademicYearActiveSchemaIT extends AbstractIntegrationTest {

    @Test
    void seededSchema_hasExactlyOneActiveYear_2026() {
        List<Integer> active =
                jdbc.queryForList("SELECT year FROM academic_years WHERE active", Integer.class);

        assertThat(active).containsExactly(2026);
    }

    @Test
    void secondActiveRow_isRejectedByTheDatabase() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO academic_years (year, active) VALUES (2027, true)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flippingAnotherYearActive_whileOneIsActive_isRejectedByTheDatabase() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE academic_years SET active = true WHERE year = 2025"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void inactiveRows_areUnlimited_andNewYearsStartClosedFree() {
        jdbc.update("INSERT INTO academic_years (year) VALUES (2027), (2028)");

        Integer inactive =
                jdbc.queryForObject(
                        "SELECT count(*) FROM academic_years WHERE year IN (2027, 2028) AND NOT active",
                        Integer.class);
        Integer closed =
                jdbc.queryForObject(
                        "SELECT count(*) FROM academic_years WHERE closed_at IS NOT NULL",
                        Integer.class);

        assertThat(inactive).isEqualTo(2);
        assertThat(closed).isZero();
    }
}
