package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.List;

/**
 * Who sat in last year's classrooms and has no seat in the reference gestión.
 *
 * @param academicYear the reference gestión: the one asked for, or the active one
 * @param previousYear the gestión with the greatest year below the reference one, or null when the
 *     reference is the first on record. Then {@code students} is empty: nobody can have failed to
 *     come back from a year that does not exist
 * @param students one row per student, sorted by last course (grade, parallel) then name
 */
public record NotReenrolledReport(
        YearRef academicYear, YearRef previousYear, List<NotReenrolledStudent> students) {

    public record YearRef(Integer id, Integer year) {}
}
