package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.CreatedAcademicYear;

/** What the Director can do to the working year. */
public interface IAcademicYearService {
    /**
     * Makes sure the gestión exists, so the Director can prepare it before the school works in it.
     * A new one is inactive and open; activating it stays a separate decision. Idempotent: asking
     * for a year that exists returns it untouched, flagged as not created.
     */
    CreatedAcademicYear create(int year);

    /**
     * Makes the year the active one. Idempotent for the year that is already active. Throws
     * ResourceNotFoundException for an unknown id and ConflictException for a closed year.
     */
    AcademicYearItem activate(int academicYearId);

    /**
     * Closes the year; if it was the active one it stops being active and no other year takes its
     * place. Idempotent: closing a closed year changes nothing, not even its closing moment. Throws
     * ResourceNotFoundException for an unknown id.
     */
    AcademicYearItem close(int academicYearId);

    /**
     * Reopens a closed year without activating it. Idempotent for a year that is already open.
     * Throws ResourceNotFoundException for an unknown id.
     */
    AcademicYearItem reopen(int academicYearId);
}
