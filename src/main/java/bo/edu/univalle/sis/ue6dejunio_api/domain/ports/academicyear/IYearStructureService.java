package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.YearStructureCopy;

/** Preparing a gestión by copying another one's classrooms and subjects into it. */
public interface IYearStructureService {
    /**
     * Copies the active courses of {@code sourceYearId}, with their active class groups, into
     * {@code targetYearId}. The copies have no homeroom teacher and no subject teachers: the
     * Director confirms those one by one afterwards. A grade and parallel the target already has is
     * skipped and reported, so running it again changes nothing.
     *
     * <p>Throws ValidationException when both ids are the same, ResourceNotFoundException for an
     * unknown year, and ConflictException when the target is closed. The source may be closed: it
     * is only read.
     */
    YearStructureCopy duplicate(int targetYearId, int sourceYearId);
}
