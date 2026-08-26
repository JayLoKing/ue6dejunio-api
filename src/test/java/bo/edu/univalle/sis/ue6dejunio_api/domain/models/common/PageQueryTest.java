package bo.edu.univalle.sis.ue6dejunio_api.domain.models.common;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void of_buildsAnUnsortedQuery() {
        PageQuery q = PageQuery.of(0, 30);

        assertThat(q.page()).isZero();
        assertThat(q.size()).isEqualTo(30);
        assertThat(q.sort()).isEmpty();
    }

    @Test
    void of_keepsTheSortOrderItWasGiven() {
        // El orden de los campos es el orden del ORDER BY: invertirlo cambia el resultado.
        PageQuery q = PageQuery.of(0, 30, SortField.asc("lastNames"), SortField.asc("names"));

        assertThat(q.sort()).containsExactly(
            new SortField("lastNames", SortDirection.ASC),
            new SortField("names", SortDirection.ASC));
    }

    @Test
    void sort_isDefensivelyCopied() {
        List<SortField> mutable = new java.util.ArrayList<>(List.of(SortField.asc("name")));
        PageQuery q = new PageQuery(0, 30, mutable);

        mutable.add(SortField.desc("id"));

        assertThat(q.sort()).containsExactly(SortField.asc("name"));
    }

    @Test
    void rejectsCoordinatesThatCannotDescribeAPage() {
        // A ValidationException, not a raw one: bad bounds are a 400, never a 500.
        assertThatThrownBy(() -> PageQuery.of(-1, 30)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> PageQuery.of(0, 0)).isInstanceOf(ValidationException.class);
    }

    @Test
    void sortField_rejectsAPropertyThatNamesNothing() {
        assertThatThrownBy(() -> SortField.asc(" ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> SortField.asc(null)).isInstanceOf(ValidationException.class);
    }
}
