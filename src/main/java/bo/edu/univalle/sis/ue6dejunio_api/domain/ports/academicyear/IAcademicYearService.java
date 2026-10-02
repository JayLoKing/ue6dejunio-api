package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;

/** What the Director can do to the working year. */
public interface IAcademicYearService {
    /**
     * Makes the year the active one. Idempotent for the year that is already active. Throws
     * ResourceNotFoundException for an unknown id and ConflictException for a closed year.
     */
    AcademicYearItem activate(int academicYearId);
}
