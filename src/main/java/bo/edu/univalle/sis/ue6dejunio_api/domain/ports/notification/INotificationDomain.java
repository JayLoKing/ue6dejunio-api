package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface INotificationDomain {
    boolean userExists(UUID userId);

    /**
     * Everyone who can act on a plan waiting for review.
     *
     * <p>All of them, not the first one found: a school with two Directors has two people who might
     * pick the review up, and telling one leaves the other blind to a plan that is waiting.
     */
    List<UUID> activeDirectorIds();

    /**
     * The active teachers who answer for a student: the homeroom teacher of every course they sat
     * in, plus the teacher of each active class group in those courses.
     *
     * <p>Both, because both keep a roster. The homeroom teacher runs the course; a technical
     * teacher runs one subject in it and marks attendance for the same student. Telling only the
     * first leaves the second calling out a name that is no longer on the roll.
     *
     * <p>Enrolments of any status, since a withdrawal is what closes them: asking for the active
     * ones after the fact would find nobody to tell.
     */
    List<UUID> teacherIdsResponsibleForStudent(UUID studentId);

    /**
     * The receiver's role, so the rule about who the Director may write to lives in the service
     * where it can be read, rather than in a query named after the rule.
     *
     * @return empty when there is no such user
     */
    Optional<String> roleNameOf(UUID userId);

    Notification send(SendNotificationCommand command);

    Optional<Notification> findById(UUID id);

    /** Stamps delivery on whatever it hands back: this is the moment the row reached its reader. */
    PageResult<Notification> listReceived(UUID receiverId, boolean unreadOnly, PageQuery pageQuery);

    long unreadCount(UUID receiverId);

    /**
     * @return the stamp the row now holds, so the caller reports what was written
     */
    LocalDateTime markAsRead(UUID id);

    int markAllRead(UUID receiverId);

    void deleteById(UUID id);
}
