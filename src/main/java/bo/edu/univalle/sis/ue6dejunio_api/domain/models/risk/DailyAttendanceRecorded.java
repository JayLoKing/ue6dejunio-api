package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The classroom-wide roll call, taken once for every subject at a time.
 *
 * <p>Names enrolments rather than a course because that is what the write is given — a daily batch
 * is a list of students and a date, and nothing in it says which classroom they came from.
 *
 * <p>It matters that this is not a {@link SessionAttendanceRecorded}: a daily row carries a null
 * {@code id_class_group}, and the attendance feature of <b>every</b> subject in that course falls
 * back to it. One roll call therefore changes what the model would say about nine subjects, not
 * one.
 */
public record DailyAttendanceRecorded(List<UUID> courseEnrollmentIds, LocalDate date)
        implements DomainEvent {

    public DailyAttendanceRecorded {
        // Copied: the caller assembled this from a request body it still holds, and an event whose
        // contents can change after it was published is a fact that stops being one.
        courseEnrollmentIds =
                courseEnrollmentIds == null ? List.of() : List.copyOf(courseEnrollmentIds);
        Objects.requireNonNull(date, "date");
    }
}
