package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;

public record AcademicYearResponse(Integer id, Integer year, boolean active, boolean closed) {
    public static AcademicYearResponse from(AcademicYearItem y) {
        return new AcademicYearResponse(y.id(), y.year(), y.active(), y.closed());
    }
}
