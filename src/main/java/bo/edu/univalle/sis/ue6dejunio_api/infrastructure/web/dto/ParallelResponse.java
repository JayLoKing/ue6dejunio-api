package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;

public record ParallelResponse(Integer id, String name) {
    public static ParallelResponse from(Parallel p) { return new ParallelResponse(p.id(), p.name()); }
}
