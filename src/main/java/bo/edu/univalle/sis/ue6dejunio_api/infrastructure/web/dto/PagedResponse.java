package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import java.util.List;

public record PagedResponse<T>(List<T> content, int page, int size, long total, int totalPages) {
    public static <T> PagedResponse<T> of(PageResult<T> p) {
        return new PagedResponse<>(
                p.content(), p.page(), p.size(), p.totalElements(), p.totalPages());
    }
}
