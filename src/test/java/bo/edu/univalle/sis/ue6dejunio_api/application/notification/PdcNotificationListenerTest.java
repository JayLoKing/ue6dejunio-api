package bo.edu.univalle.sis.ue6dejunio_api.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.notification.NotificationDispatcher;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.notification.PdcNotificationListener;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatusChanged;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdcNotificationListenerTest {

    @Mock private NotificationDispatcher dispatcher;
    @Mock private INotificationDomain notificationDomain;
    @InjectMocks private PdcNotificationListener listener;
    @Captor private ArgumentCaptor<SendNotificationCommand> sent;

    private static PdcStatusChanged handedIn() {
        return new PdcStatusChanged(
                UUID.randomUUID(), UUID.randomUUID(), PdcStatus.PUBLISHED, 4, 2, null);
    }

    /**
     * Telling three people is three outcomes, not one.
     *
     * <p>The listener used to open a transaction around the whole loop and catch inside it. A send
     * that failed marked that transaction rollback-only, so the row already written for the first
     * Director went down with it — and the catch hid the failure until the boundary threw on commit
     * anyway. Each send now stands alone.
     */
    @Test
    void aDirectorWhoCannotBeToldDoesNotCostTheOthersTheirNotification() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(notificationDomain.activeDirectorIds()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("boom"))
                .doNothing()
                .when(dispatcher)
                .deliver(any(SendNotificationCommand.class));

        assertThatCode(() -> listener.onPdcStatusChanged(handedIn())).doesNotThrowAnyException();

        verify(dispatcher, org.mockito.Mockito.times(2))
                .deliver(any(SendNotificationCommand.class));
    }

    // A failure here cannot undo a plan that is already committed and correct, so it must not
    // become an error the caller is handed.
    @Test
    void aFailedSendIsNotRaisedToWhoeverChangedThePlan() {
        when(notificationDomain.activeDirectorIds()).thenReturn(List.of(UUID.randomUUID()));
        doThrow(new IllegalStateException("boom"))
                .when(dispatcher)
                .deliver(any(SendNotificationCommand.class));

        assertThatCode(() -> listener.onPdcStatusChanged(handedIn())).doesNotThrowAnyException();
    }

    @Test
    void whatTheSystemWritesCarriesNoSenderAndPointsAtThePlan() {
        UUID director = UUID.randomUUID();
        when(notificationDomain.activeDirectorIds()).thenReturn(List.of(director));
        doNothing().when(dispatcher).deliver(sent.capture());

        PdcStatusChanged event = handedIn();
        listener.onPdcStatusChanged(event);

        assertThat(sent.getValue().senderId()).isNull();
        assertThat(sent.getValue().receiverId()).isEqualTo(director);
        assertThat(sent.getValue().resourceType()).isEqualTo("CURRICULUM_PLAN");
        assertThat(sent.getValue().resourceId()).isEqualTo(event.planId());
    }

    // A plan whose author is gone still changed status. Nobody to tell is not a reason to throw
    // inside a listener that runs after the change already committed.
    @Test
    void aPlanWithNoAuthorIsNotAnnouncedAndDoesNotBreak() {
        PdcStatusChanged orphan =
                new PdcStatusChanged(UUID.randomUUID(), null, PdcStatus.APPROVED, 4, 2, null);

        assertThatCode(() -> listener.onPdcStatusChanged(orphan)).doesNotThrowAnyException();

        verify(dispatcher, org.mockito.Mockito.never()).deliver(any(SendNotificationCommand.class));
    }

    // A status nobody is waiting on says nothing rather than falling into a default branch that
    // would announce whatever gets added to the list next.
    @Test
    void aDraftAnnouncesNothing() {
        PdcStatusChanged draft =
                new PdcStatusChanged(
                        UUID.randomUUID(), UUID.randomUUID(), PdcStatus.DRAFT, 4, 2, null);

        listener.onPdcStatusChanged(draft);

        verify(dispatcher, org.mockito.Mockito.never()).deliver(any(SendNotificationCommand.class));
    }
}
