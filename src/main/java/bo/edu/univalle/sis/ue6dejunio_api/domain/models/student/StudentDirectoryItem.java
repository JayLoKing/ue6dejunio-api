package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

/**
 * One row of a student listing.
 *
 * <p>Carries the identity card because the school looks students up by it as often as by RUDE, and
 * the status because a listing that spans the whole institution shows people who already left.
 *
 * @param grade the course they sit in, or sat in. Absent for a student with no enrolment at all
 */
public record StudentDirectoryItem(
    UUID id,
    String rudeCode,
    String identityCard,
    String fullName,
    String grade,
    String parallel,
    String level,
    String status
) {}
