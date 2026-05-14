package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long total,
    int totalPages
) {
    public static <T> PagedResponse<T> of(Page<T> p) {
        return new PagedResponse<>(
            p.getContent(),
            p.getNumber(),
            p.getSize(),
            p.getTotalElements(),
            p.getTotalPages()
        );
    }
}
