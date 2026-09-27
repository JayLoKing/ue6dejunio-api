package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import java.util.UUID;

/**
 * RF 37 — the attendance percentage of every student in a course, trimestral or annual.
 *
 * <p>{@code scope} is {@code "trimester"} or {@code "annual"} and {@code trimester} is null for the
 * annual one, the same pair {@link CourseAttendanceStats} reports. They are carried rather than
 * left to the caller because the document states its own scope, and a report that does not say
 * which months it covers cannot be checked against anything.
 *
 * <p>Both read the same rows through the same period filter, so adding up every student's counts
 * returns the panel's {@code overall} exactly — across the whole roll, not within one page. This
 * carries a page, so the sum reconciles only once every page has been read; a document that prints
 * the report asks for the roll in one wide page for exactly that reason.
 */
public record CourseStudentAttendance(
        UUID courseId,
        String scope,
        Integer trimester,
        PageResult<StudentAttendanceSummary> students) {}
