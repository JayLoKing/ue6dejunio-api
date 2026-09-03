package bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;

import java.util.UUID;

/**
 * A notification reached someone's inbox.
 *
 * <p>Carries the receiver and nothing else worth reading. Whoever handles this is telling a browser
 * that there is something new, not handing it the contents: the stream is a nudge, and the inbox
 * the reader then fetches is the one place the message actually lives. Putting the text on the wire
 * would mean an open tab could be shown a notification the reader is no longer entitled to.
 *
 * @param receiverId whose inbox grew
 * @param notificationId the row that was written, so a log can be traced back to it
 */
public record NotificationSent(UUID receiverId, UUID notificationId) implements DomainEvent {
}
