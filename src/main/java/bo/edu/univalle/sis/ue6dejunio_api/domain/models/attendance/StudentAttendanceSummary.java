package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.util.UUID;

/**
 * One student's attendance over the report's scope: the totals and the percentage RF 37 asks for.
 *
 * <p>Carries the enrolment as well as the student because the counts belong to the enrolment — the
 * same child in two courses of one gestión has two of these — while the name is what the document
 * prints.
 *
 * <p>{@code counts.percentage()} is null for a student with no computable day, never zero: nobody
 * marked them, which is not the same as them never showing up.
 */
public record StudentAttendanceSummary(
        UUID courseEnrollmentId, UUID studentId, String studentName, AttendanceCounts counts) {}
