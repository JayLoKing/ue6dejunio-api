package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SpringPagingTest {

    @Test
    void toPageable_carriesPageAndSize() {
        Pageable p = SpringPaging.toPageable(PageQuery.of(2, 25));

        assertThat(p.getPageNumber()).isEqualTo(2);
        assertThat(p.getPageSize()).isEqualTo(25);
        assertThat(p.getSort().isUnsorted()).isTrue();
    }

    @Test
    void toPageable_keepsEveryFieldInTheOrderGiven() {
        // El orden de los campos ES el ORDER BY: perderlo cambia las filas que devuelve la pagina.
        Pageable p =
                SpringPaging.toPageable(
                        PageQuery.of(
                                0,
                                30,
                                SortField.asc("student.lastNames"),
                                SortField.desc("updatedAt")));

        assertThat(p.getSort())
                .containsExactly(Sort.Order.asc("student.lastNames"), Sort.Order.desc("updatedAt"));
    }

    @Test
    void toPageResult_keepsTheCoordinatesSpringReports() {
        Page<String> page =
                new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2, Sort.by("name")), 7);

        PageResult<String> result = SpringPaging.toPageResult(page);

        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(7);
    }

    @Test
    void toPageResult_agreesWithSpringOnHowManyPagesThereAre() {
        Page<String> page = new PageImpl<>(List.of("a"), PageRequest.of(0, 10), 21);

        assertThat(SpringPaging.toPageResult(page).totalPages()).isEqualTo(page.getTotalPages());
    }

    @Test
    void toPageResult_ofAnEmptyPageAgreesWithSpring() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(3, 25), 0);

        PageResult<String> result = SpringPaging.toPageResult(page);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(page.getNumber());
        assertThat(result.size()).isEqualTo(page.getSize());
        assertThat(result.totalElements()).isEqualTo(page.getTotalElements());
        assertThat(result.totalPages()).isEqualTo(page.getTotalPages());
    }
}
