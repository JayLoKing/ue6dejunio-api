package bo.edu.univalle.sis.ue6dejunio_api.domain.models.common;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import java.util.List;

/**
 * A request for one page of results: which page, how big, and in what order.
 *
 * <p>This is the domain's own way of asking for a page. The ports speak it so the domain does not
 * have to compile against the persistence framework; the adapter translates it at the edge.
 *
 * @param page zero-based page index
 * @param size how many rows the page holds, at least one
 * @param sort ordering fields, applied in the order given — that order is the {@code ORDER BY}
 */
public record PageQuery(int page, int size, List<SortField> sort) {

    public PageQuery {
        if (page < 0) {
            throw new ValidationException("Page index cannot be negative: " + page);
        }
        if (size < 1) {
            throw new ValidationException("Page size must be at least 1: " + size);
        }
        sort = sort == null ? List.of() : List.copyOf(sort);
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size, List.of());
    }

    public static PageQuery of(int page, int size, SortField... sort) {
        return new PageQuery(page, size, List.of(sort));
    }
}
