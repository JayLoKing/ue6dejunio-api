package bo.edu.univalle.sis.ue6dejunio_api.application.notification;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.notification.NotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private INotificationDomain notificationDomain;
    @InjectMocks private NotificationService notificationService;

    private Notification notif(UUID id, UUID receiver) {
        return new Notification(id, UUID.randomUUID(), "Sender",
            receiver, "Receiver", "msg", false, LocalDateTime.now());
    }

    @Test
    void send_receiverExists_saves() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.userExists(receiver)).thenReturn(true);
        when(notificationDomain.send(sender, receiver, "hola"))
            .thenReturn(notif(UUID.randomUUID(), receiver));
        Notification r = notificationService.send(new SendNotificationCommand(sender, receiver, "hola"));
        assertThat(r.message()).isEqualTo("msg");
    }

    @Test
    void send_receiverMissing_throws() {
        UUID receiver = UUID.randomUUID();
        when(notificationDomain.userExists(receiver)).thenReturn(false);
        assertThatThrownBy(() -> notificationService.send(
            new SendNotificationCommand(UUID.randomUUID(), receiver, "x")))
            .isInstanceOf(ResourceNotFoundException.class);
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
