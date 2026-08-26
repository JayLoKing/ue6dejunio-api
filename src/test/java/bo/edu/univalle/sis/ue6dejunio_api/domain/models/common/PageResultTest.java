package bo.edu.univalle.sis.ue6dejunio_api.domain.models.common;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResultTest {

    @Test
    void totalPages_roundsUpTheLastPartialPage() {
        assertThat(new PageResult<>(List.of("a"), 0, 10, 21).totalPages()).isEqualTo(3);
    }

    @Test
    void totalPages_ofAnExactMultipleDoesNotAddAnEmptyPage() {
        assertThat(new PageResult<>(List.of("a"), 0, 10, 20).totalPages()).isEqualTo(2);
    }

    @Test
    void totalPages_ofAnEmptyResultIsZero() {
        assertThat(new PageResult<>(List.of(), 0, 10, 0).totalPages()).isZero();
    }

    @Test
    void map_convertsTheContentAndKeepsTheCoordinates() {
        PageResult<Integer> source = new PageResult<>(List.of(1, 2, 3), 2, 10, 57);

        PageResult<String> mapped = source.map(String::valueOf);

        assertThat(mapped.content()).containsExactly("1", "2", "3");
        assertThat(mapped.page()).isEqualTo(2);
        assertThat(mapped.size()).isEqualTo(10);
        assertThat(mapped.totalElements()).isEqualTo(57);
        assertThat(mapped.totalPages()).isEqualTo(6);
    }

    @Test
    void empty_keepsThePageCoordinatesAsked() {
        // El controlador que no encuentra nada igual debe responder la pagina pedida,
        // no una pagina cero inventada.
        PageResult<String> empty = PageResult.empty(new PageQuery(3, 25, List.of()));

        assertThat(empty.content()).isEmpty();
        assertThat(empty.page()).isEqualTo(3);
        assertThat(empty.size()).isEqualTo(25);
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.totalPages()).isZero();
    }

    @Test
    void content_isDefensivelyCopied() {
        List<String> mutable = new java.util.ArrayList<>(List.of("a"));
        PageResult<String> result = new PageResult<>(mutable, 0, 10, 1);

        mutable.add("b");

        assertThat(result.content()).containsExactly("a");
    }

    @Test
    void rejectsCoordinatesThatCannotDescribeAPage() {
        // A ValidationException, not a raw one: bad bounds are a 400, never a 500.
        assertThatThrownBy(() -> new PageResult<>(List.of(), -1, 10, 0))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 0, 0))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 10, -1))
            .isInstanceOf(ValidationException.class);
    }
}
