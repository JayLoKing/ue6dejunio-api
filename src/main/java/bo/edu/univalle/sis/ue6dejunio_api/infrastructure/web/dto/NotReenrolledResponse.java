package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.NotReenrolledReport;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.NotReenrolledStudent;
import java.util.List;
import java.util.UUID;

/**
 * Who did not come back.
 *
 * @param previousYear null when the reference gestión is the first on record; {@code students} is
 *     then empty
 * @param students sorted by last course then name
 */
public record NotReenrolledResponse(
        YearResponse academicYear, YearResponse previousYear, List<StudentRow> students) {

    public record YearResponse(Integer id, Integer year) {}

    /** {@code name} is grade and parallel as the rest of the app labels a course. */
    public record CourseResponse(UUID id, String name, String grade, String parallel) {}

    /**
     * @param status "Effective", or "Withdrawn" for a student who left after last year ended
     */
    public record StudentRow(
            UUID studentId,
            String rudeCode,
            String identityCard,
            String fullName,
            String status,
            CourseResponse lastCourse) {}

    public static NotReenrolledResponse from(NotReenrolledReport r) {
        return new NotReenrolledResponse(
                year(r.academicYear()),
                year(r.previousYear()),
                r.students().stream().map(NotReenrolledResponse::row).toList());
    }

    private static YearResponse year(NotReenrolledReport.YearRef ref) {
        return ref == null ? null : new YearResponse(ref.id(), ref.year());
    }

    private static StudentRow row(NotReenrolledStudent s) {
        NotReenrolledStudent.LastCourse c = s.lastCourse();
        return new StudentRow(
                s.studentId(),
                s.rudeCode(),
                s.identityCard(),
                s.fullName(),
                s.status(),
                new CourseResponse(c.id(), c.name(), c.grade(), c.parallel()));
    }
}
