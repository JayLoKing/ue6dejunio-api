package bo.edu.univalle.sis.ue6dejunio_api.domain.models.common;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import java.util.List;
import java.util.function.Function;

/**
 * One page of results: its rows plus the coordinates needed to place them in the whole set.
 *
 * <p>The domain's counterpart to a framework page. {@code totalPages} is derived rather than
 * stored, so it can never disagree with {@code totalElements} and {@code size}.
 *
 * @param page zero-based index of this page
 * @param size the page size that was asked for, not the number of rows that came back
 * @param totalElements how many rows exist across every page
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public PageResult {
        if (page < 0) {
            throw new ValidationException("Page index cannot be negative: " + page);
        }
        if (size < 1) {
            throw new ValidationException("Page size must be at least 1: " + size);
        }
        if (totalElements < 0) {
            throw new ValidationException("Total elements cannot be negative: " + totalElements);
        }
        content = content == null ? List.of() : List.copyOf(content);
    }

    /**
     * The page a query found nothing for. Keeps the coordinates asked, so the answer stays honest.
     */
    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), query.page(), query.size(), 0L);
    }

    /** How many pages the whole set spans. Zero when there is nothing to page through. */
    public int totalPages() {
        return (int) Math.ceil((double) totalElements / (double) size);
    }

    /**
     * Same page, rows converted. Used to turn domain rows into responses without losing the page.
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements);
    }
}
