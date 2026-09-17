package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.util.List;
import java.util.UUID;

/**
 * One row of section IV: a student who failed something this trimester, what they failed, and what
 * the teacher did about it.
 *
 * <p>Half derived and half written. {@code failedAreas} comes from the marks; {@code actions} and
 * {@code verificationSource} are the teacher's own two columns, carried over from the stored
 * report and null until they write them.
 *
 * @param number the line number the sheet prints, from one. Assigned after the rows are ordered,
 *               so it names a place on this document rather than anything about the student.
 */
public record FailingStudentRow(
    int number,
    UUID courseEnrollmentId,
    UUID studentId,
    String fullName,
    List<FailedArea> failedAreas,
    String actions,
    String verificationSource
) {}
