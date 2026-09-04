package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

/**
 * One row of a student listing.
 *
 * <p>Carries the identity card because the school looks students up by it as often as by RUDE, and
 * the status because a listing that spans the whole institution shows people who already left.
 *
 * @param grade the course they sit in, or sat in. Absent for a student with no enrolment at all
 * @param academicYear the gestión the grade and parallel above belong to. A student who moves up
 *                     keeps this record and gains another year, so the grade means nothing without
 *                     the year it was true of
 */
public record StudentDirectoryItem(
    UUID id,
    String rudeCode,
    String identityCard,
    String fullName,
    String grade,
    String parallel,
    String level,
    String status,
    Integer academicYear
) {}
