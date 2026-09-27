package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.CourseStudentAttendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.StudentAttendanceSummary;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * RF 37 — the per-student attendance report of a course.
 *
 * <p>Mirrors {@link CourseAttendanceStatsResponse}'s {@code scope}/{@code trimester} pair, and each
 * student carries the same six count fields that response's {@code Counts} does, flattened into the
 * row because a document prints a row and not a nested object.
 *
 * <p>{@code percentage} is null, never zero, for a student with no computable day: nobody recorded
 * them, which is a different fact from never having shown up.
 */
public record CourseStudentAttendanceResponse(
        UUID courseId, String scope, Integer trimester, PagedResponse<StudentRow> students) {

    public record StudentRow(
            UUID courseEnrollmentId,
            UUID studentId,
            String studentName,
            long present,
            long absent,
            long late,
            long excused,
            long computableSessions,
            BigDecimal percentage) {
        static StudentRow from(StudentAttendanceSummary s) {
            return new StudentRow(
                    s.courseEnrollmentId(),
                    s.studentId(),
                    s.studentName(),
                    s.counts().present(),
                    s.counts().absent(),
                    s.counts().late(),
                    s.counts().excused(),
                    s.counts().computableSessions(),
                    s.counts().percentage());
        }
    }

    public static CourseStudentAttendanceResponse from(CourseStudentAttendance report) {
        return new CourseStudentAttendanceResponse(
                report.courseId(),
                report.scope(),
                report.trimester(),
                PagedResponse.of(report.students().map(StudentRow::from)));
    }
}
