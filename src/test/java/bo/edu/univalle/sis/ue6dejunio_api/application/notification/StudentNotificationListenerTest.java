package bo.edu.univalle.sis.ue6dejunio_api.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.notification.NotificationDispatcher;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.notification.StudentNotificationListener;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentReinstated;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawn;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentNotificationListenerTest {

    @Mock private NotificationDispatcher dispatcher;
    @Mock private INotificationDomain notificationDomain;
    @InjectMocks private StudentNotificationListener listener;

    private static StudentWithdrawn withdrawn(UUID studentId, String note) {
        return new StudentWithdrawn(studentId, "Ana Quispe", "Transferencia", note);
    }

    /** Both rosters shrink, so both teachers are told: the course's and the subject's. */
    @Test
    void onStudentWithdrawn_tellsEveryTeacherWhoAnsweredForThem() {
        UUID student = UUID.randomUUID();
        UUID homeroom = UUID.randomUUID();
        UUID technical = UUID.randomUUID();
        when(notificationDomain.teacherIdsResponsibleForStudent(student))
                .thenReturn(List.of(homeroom, technical));

        listener.onStudentWithdrawn(withdrawn(student, null));

        verify(dispatcher, times(2)).deliver(any(SendNotificationCommand.class));
    }

    /** The notice has to say who left and why, or it is a teacher opening a roster to find out. */
    @Test
    void onStudentWithdrawn_namesTheStudentAndTheReason() {
        UUID student = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        when(notificationDomain.teacherIdsResponsibleForStudent(student))
                .thenReturn(List.of(teacher));

        listener.onStudentWithdrawn(withdrawn(student, null));

        ArgumentCaptor<SendNotificationCommand> sent =
                ArgumentCaptor.forClass(SendNotificationCommand.class);
        verify(dispatcher).deliver(sent.capture());
        SendNotificationCommand command = sent.getValue();

        assertThat(command.receiverId()).isEqualTo(teacher);
        assertThat(command.type()).isEqualTo(NotificationType.STUDENT_WITHDRAWN);
        // No sender: the withdrawal wrote this, and a name on it would credit somebody for a line
        // nobody typed.
        assertThat(command.senderId()).isNull();
        assertThat(command.message()).contains("Ana Quispe").contains("Transferencia");
        assertThat(command.resourceId()).isEqualTo(student);
    }

    /** The Director's own words travel with it, when there were any. */
    @Test
    void onStudentWithdrawn_carriesTheNoteWhenThereIsOne() {
        UUID student = UUID.randomUUID();
        when(notificationDomain.teacherIdsResponsibleForStudent(student))
                .thenReturn(List.of(UUID.randomUUID()));

        listener.onStudentWithdrawn(withdrawn(student, "Se mudó a Santa Cruz."));

        ArgumentCaptor<SendNotificationCommand> sent =
                ArgumentCaptor.forClass(SendNotificationCommand.class);
        verify(dispatcher).deliver(sent.capture());
        assertThat(sent.getValue().message()).contains("Se mudó a Santa Cruz.");
    }

    /** A student nobody was teaching. Not an error, and not worth a log at error level. */
    @Test
    void onStudentWithdrawn_withNobodyToTell_sendsNothing() {
        UUID student = UUID.randomUUID();
        when(notificationDomain.teacherIdsResponsibleForStudent(student)).thenReturn(List.of());

        listener.onStudentWithdrawn(withdrawn(student, null));

        verify(dispatcher, never()).deliver(any());
    }

    /**
     * One recipient, one outcome. The student is already withdrawn and correct; a message that
     * could not be written cannot undo that, and it must not cost the other teachers theirs.
     */
    @Test
    void onStudentWithdrawn_aFailedSendDoesNotStopTheRest() {
        UUID student = UUID.randomUUID();
        when(notificationDomain.teacherIdsResponsibleForStudent(student))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        doThrow(new RuntimeException("boom"))
                .when(dispatcher)
                .deliver(any(SendNotificationCommand.class));

        listener.onStudentWithdrawn(withdrawn(student, null));

        verify(dispatcher, times(2)).deliver(any(SendNotificationCommand.class));
    }

    private static StudentReinstated reinstated(UUID studentId, UUID courseId) {
        return new StudentReinstated(studentId, "Ana Quispe", courseId, "Primero A");
    }

    /** The roster grows in the reopened course only: its teachers are the ones told. */
    @Test
    void onStudentReinstated_tellsTheTeachersOfThatCourseAndNamesTheStudentAndCourse() {
        UUID student = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        UUID homeroom = UUID.randomUUID();
        UUID technical = UUID.randomUUID();
        when(notificationDomain.teacherIdsResponsibleForCourse(course))
                .thenReturn(List.of(homeroom, technical));

        listener.onStudentReinstated(reinstated(student, course));

        ArgumentCaptor<SendNotificationCommand> sent =
                ArgumentCaptor.forClass(SendNotificationCommand.class);
        verify(dispatcher, times(2)).deliver(sent.capture());
        SendNotificationCommand first = sent.getAllValues().getFirst();
        assertThat(sent.getAllValues())
                .extracting(SendNotificationCommand::receiverId)
                .containsExactly(homeroom, technical);
        assertThat(first.type()).isEqualTo(NotificationType.STUDENT_REINSTATED);
        assertThat(first.senderId()).isNull();
        assertThat(first.message()).isEqualTo("Reincorporación: Ana Quispe vuelve a Primero A.");
        assertThat(first.resourceType()).isEqualTo("STUDENT");
        assertThat(first.resourceId()).isEqualTo(student);
    }

    @Test
    void onStudentReinstated_withNobodyToTell_sendsNothing() {
        UUID course = UUID.randomUUID();
        when(notificationDomain.teacherIdsResponsibleForCourse(course)).thenReturn(List.of());

        listener.onStudentReinstated(reinstated(UUID.randomUUID(), course));

        verify(dispatcher, never()).deliver(any());
    }

    @Test
    void onStudentReinstated_aFailedSendDoesNotStopTheRest() {
        UUID course = UUID.randomUUID();
        when(notificationDomain.teacherIdsResponsibleForCourse(course))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        doThrow(new RuntimeException("boom"))
                .when(dispatcher)
                .deliver(any(SendNotificationCommand.class));

        listener.onStudentReinstated(reinstated(UUID.randomUUID(), course));

        verify(dispatcher, times(2)).deliver(any(SendNotificationCommand.class));
    }
}
