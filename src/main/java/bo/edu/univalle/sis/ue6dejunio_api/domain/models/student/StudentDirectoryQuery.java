package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

/**
 * What a directory listing is looking for.
 *
 * <p>One value rather than six parameters. Every one of these is optional and independent, and a
 * signature of six nullable arguments is one the caller gets wrong silently — three of them are
 * {@code Integer} and would swap without a word from the compiler.
 *
 * @param q matched against names, last names, RUDE and identity card. Blank means everyone
 * @param courseId narrows to one course. Also how a teacher is pinned to their own. A course sits
 *     in exactly one gestión, so this pins the year as well
 * @param gradeId narrows to a grade across every parallel
 * @param parallelId narrows to a parallel across every grade
 * @param academicYearId which gestión the listing is about. A student's grade and parallel are only
 *     true of one year, so a listing that spans them all shows the same student once per enrolment.
 *     Resolved to the current gestión by the application when the caller names neither this nor a
 *     course
 * @param scope which students are being asked about. Never {@code null} — see {@link
 *     StudentDirectoryScope#ACTIVE}, which is what a caller who says nothing means
 */
public record StudentDirectoryQuery(
        String q,
        UUID courseId,
        Integer gradeId,
        Integer parallelId,
        Integer academicYearId,
        StudentDirectoryScope scope) {
    public StudentDirectoryQuery {
        scope = scope == null ? StudentDirectoryScope.ACTIVE : scope;
    }

    /** The listing a caller who filtered by nothing but the course is asking for. */
    public static StudentDirectoryQuery of(String q, UUID courseId) {
        return new StudentDirectoryQuery(
                q, courseId, null, null, null, StudentDirectoryScope.ACTIVE);
    }

    /** The same listing, said of one gestión. */
    public StudentDirectoryQuery inAcademicYear(Integer academicYearId) {
        return new StudentDirectoryQuery(q, courseId, gradeId, parallelId, academicYearId, scope);
    }
}
