package bo.edu.univalle.sis.ue6dejunio_api.application.notification;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.notification.NotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationSent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private INotificationDomain notificationDomain;
    @Mock private IDomainEventPublisher events;
    @InjectMocks private NotificationService notificationService;

    private Notification notif(UUID id, UUID receiver) {
        return new Notification(id, UUID.randomUUID(), "Sender",
            receiver, "Receiver", NotificationType.SUMMONS, null, "msg",
            null, null, null, null, LocalDateTime.now());
    }

    private static SendNotificationCommand summons(UUID sender, UUID receiver) {
        return new SendNotificationCommand(sender, receiver, NotificationType.SUMMONS,
            null, "hola", null, null);
    }

    /** A receiver the Director is allowed to write to, which every send below assumes. */
    private void receiverIsATeacher(UUID receiver) {
        when(notificationDomain.userExists(receiver)).thenReturn(true);
        when(notificationDomain.roleNameOf(receiver)).thenReturn(Optional.of("Teacher"));
    }

    @Test
    void send_receiverExists_saves() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        receiverIsATeacher(receiver);
        when(notificationDomain.send(any(SendNotificationCommand.class)))
            .thenReturn(notif(UUID.randomUUID(), receiver));
        Notification r = notificationService.send(summons(sender, receiver));
        assertThat(r.message()).isEqualTo("msg");
    }

    // The row is written and then the fact is stated, so a browser holding the stream open can be
    // told to go and read it. The event names the receiver only: the stream is a nudge, and what
    // was actually said stays behind the inbox the reader is authorised for.
    @Test
    void send_written_announcesItSoAnOpenTabCanBeNudged() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID written = UUID.randomUUID();
        receiverIsATeacher(receiver);
        when(notificationDomain.send(any(SendNotificationCommand.class)))
            .thenReturn(notif(written, receiver));

        notificationService.send(summons(sender, receiver));

        verify(events).publish(new NotificationSent(receiver, written));
    }

    // Nothing was written, so there is nothing to go and read. Announcing a refused send would
    // spend a round trip per rejected message on every tab the receiver has open.
    @Test
    void send_refused_announcesNothing() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.userExists(receiver)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.send(summons(sender, receiver)))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(events, never()).publish(any());
    }

    // The listener writes these with no sender, and they are the one thing a person may not put
    // in an inbox by hand. Nothing about the receiver's role applies to them.
    @Test
    void send_whatTheSystemWrites_skipsTheRulesAboutPeople() {
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.userExists(receiver)).thenReturn(true);
        when(notificationDomain.send(any(SendNotificationCommand.class)))
            .thenReturn(notif(UUID.randomUUID(), receiver));

        assertThat(notificationService.send(new SendNotificationCommand(
            null, receiver, NotificationType.PDC_APPROVED, null, "hola", null, null)))
            .isNotNull();
    }

    @Test
    void send_aPersonWritingWhatTheSystemAnnounces_throws() {
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.userExists(receiver)).thenReturn(true);

        assertThatThrownBy(() -> notificationService.send(new SendNotificationCommand(
            UUID.randomUUID(), receiver, NotificationType.PDC_APPROVED, null, "hola", null, null)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void send_toSomebodyWhoDoesNotAnswerToTheDirector_throws() {
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.userExists(receiver)).thenReturn(true);
        when(notificationDomain.roleNameOf(receiver)).thenReturn(Optional.of("Director"));

        assertThatThrownBy(() -> notificationService.send(summons(UUID.randomUUID(), receiver)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void send_receiverMissing_throws() {
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.userExists(receiver)).thenReturn(false);
        assertThatThrownBy(() -> notificationService.send(summons(UUID.randomUUID(), receiver)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // The catalog types are their own heading. CUSTOM is the one the sender has to name, or the
    // row lands in an inbox indistinguishable from the one under it.
    @Test
    void send_customWithoutASubject_throws() {
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.userExists(receiver)).thenReturn(true);

        assertThatThrownBy(() -> notificationService.send(new SendNotificationCommand(
            UUID.randomUUID(), receiver, NotificationType.CUSTOM, "   ", "hola", null, null)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void send_catalogTypeWithoutASubject_isAccepted() {
        UUID receiver = UUID.randomUUID();
        receiverIsATeacher(receiver);
        when(notificationDomain.send(any(SendNotificationCommand.class)))
            .thenReturn(notif(UUID.randomUUID(), receiver));

        assertThat(notificationService.send(summons(UUID.randomUUID(), receiver))).isNotNull();
    }

    @Test
    void markRead_receiver_marks() {
        UUID id = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.findById(id)).thenReturn(Optional.of(notif(id, receiver)));
        notificationService.markRead(id);
        verify(notificationDomain).markAsRead(id);
    }

    @Test
    void markRead_unknownNotification_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(notificationDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(id))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_unknownNotification_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(notificationDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete(id))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
