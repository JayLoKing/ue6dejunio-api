package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortDirection;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

/**
 * The one place where the domain's paging vocabulary meets Spring Data's.
 *
 * <p>Keeping the translation here is what lets the ports stay free of {@code Page} and
 * {@code Pageable}: the adapter speaks both languages, nothing above it has to.
 */
public final class SpringPaging {

    public static Pageable toPageable(PageQuery query) {
        List<Sort.Order> orders = query.sort().stream().map(SpringPaging::toOrder).toList();
        // Sort.by(List) on an empty list yields Sort.unsorted(), which is what an
        // unordered PageQuery means.
        return PageRequest.of(query.page(), query.size(), Sort.by(orders));
    }

    public static <T> PageResult<T> toPageResult(Page<T> page) {
        return new PageResult<>(
            page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private static Sort.Order toOrder(SortField field) {
        return field.direction() == SortDirection.DESC
            ? Sort.Order.desc(field.property())
            : Sort.Order.asc(field.property());
    }

    private SpringPaging() {}
}
