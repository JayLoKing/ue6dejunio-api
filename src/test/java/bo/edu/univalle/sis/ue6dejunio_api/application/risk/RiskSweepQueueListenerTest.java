package bo.edu.univalle.sis.ue6dejunio_api.application.risk;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.risk.RiskSweepQueueListener;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.DailyAttendanceRecorded;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskInputsChanged;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SessionAttendanceRecorded;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepQueueDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskSweepQueueListenerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 15);

    private IRiskSweepQueueDomain queue;
    private RiskSweepQueueListener listener;

    private final UUID classGroup = UUID.randomUUID();
    private final UUID enrollment = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queue = mock(IRiskSweepQueueDomain.class);
        listener = new RiskSweepQueueListener(queue);
    }

    @Test
    @DisplayName("a changed mark queues its own subject and trimester")
    void onRiskInputsChanged_marksTheSubject() {
        listener.onRiskInputsChanged(new RiskInputsChanged(classGroup, 2));

        verify(queue).markClassGroups(List.of(classGroup), 2);
    }

    /** A roll call inside a subject knows its subject; the trimester is the date's to give. */
    @Test
    @DisplayName("a session roll call queues by date, not by trimester")
    void onSessionAttendanceRecorded_marksByDate() {
        listener.onSessionAttendanceRecorded(new SessionAttendanceRecorded(classGroup, DATE));

        verify(queue).markClassGroupsOn(List.of(classGroup), DATE);
    }

    /** The daily roll call names students, and every subject of their course moves with it. */
    @Test
    @DisplayName("a daily roll call queues the courses its students belong to")
    void onDailyAttendanceRecorded_marksTheCourses() {
        listener.onDailyAttendanceRecorded(new DailyAttendanceRecorded(List.of(enrollment), DATE));

        verify(queue).markCoursesOfEnrollmentsOn(List.of(enrollment), DATE);
    }

    /**
     * The write this stands for is already committed. A queue row that could not be written is a
     * subject that will not be re-predicted until something touches it again — worth an error in
     * the log, never worth surfacing as the failure of a save that succeeded.
     */
    @Test
    @DisplayName("a queue that refuses the mark does not fail the save behind it")
    void mark_whenTheQueueRefuses_swallowsAndLogs() {
        doThrow(new IllegalStateException("the queue is unreachable"))
                .when(queue)
                .markClassGroups(any(), anyInt());

        assertThatCode(() -> listener.onRiskInputsChanged(new RiskInputsChanged(classGroup, 1)))
                .doesNotThrowAnyException();
    }
}
