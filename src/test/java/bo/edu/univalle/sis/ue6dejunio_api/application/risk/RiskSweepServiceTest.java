package bo.edu.univalle.sis.ue6dejunio_api.application.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.risk.RiskSweepService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelUnavailableException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepTarget;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService.RunSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepQueueDomain;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RiskSweepServiceTest {

    private static final int BATCH = 200;

    private IRiskSweepQueueDomain queue;
    private IRiskPredictionService predictions;
    private RiskSweepService service;

    private final UUID groupA = UUID.randomUUID();
    private final UUID groupB = UUID.randomUUID();
    private final UUID groupC = UUID.randomUUID();
    private final LocalDateTime marked = LocalDateTime.of(2026, 9, 17, 8, 0);

    @BeforeEach
    void setUp() {
        queue = org.mockito.Mockito.mock(IRiskSweepQueueDomain.class);
        predictions = org.mockito.Mockito.mock(IRiskPredictionService.class);
        service = new RiskSweepService(queue, predictions, BATCH);
    }

    @Test
    @DisplayName("an empty queue asks the model nothing")
    void sweep_emptyQueue_neverTouchesTheModel() {
        when(queue.pending(BATCH)).thenReturn(List.of());

        SweepSummary summary = service.sweep();

        assertThat(summary.isEmpty()).isTrue();
        verifyNoInteractions(predictions);
        verify(queue, never()).clearSwept(any(), anyInt(), any());
    }

    /**
     * The point of the whole design. Three subjects standing in the queue are one call to the
     * model, not three — a drain that looped would undo the coalescing the queue exists to provide.
     */
    @Test
    @DisplayName("subjects of one trimester go to the model in a single call")
    void sweep_sameTrimester_sendsOneBatch() {
        when(queue.pending(BATCH))
                .thenReturn(
                        List.of(
                                new SweepTarget(groupA, 1, marked),
                                new SweepTarget(groupB, 1, marked),
                                new SweepTarget(groupC, 1, marked)));
        when(predictions.predictClassGroups(any(), eq(1))).thenReturn(new RunSummary(30, 2, 28, 4));

        SweepSummary summary = service.sweep();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> sent = ArgumentCaptor.forClass(Collection.class);
        verify(predictions).predictClassGroups(sent.capture(), eq(1));
        assertThat(sent.getValue()).containsExactlyInAnyOrder(groupA, groupB, groupC);
        assertThat(summary.subjects()).isEqualTo(3);
        assertThat(summary.predicted()).isEqualTo(28);
        assertThat(summary.transitions()).isEqualTo(4);
    }

    /**
     * The model is asked per trimester: a vector's trimester decides which marks it was built on.
     */
    @Test
    @DisplayName("subjects of different trimesters are split into one call each")
    void sweep_mixedTrimesters_oneCallPerTrimester() {
        when(queue.pending(BATCH))
                .thenReturn(
                        List.of(
                                new SweepTarget(groupA, 1, marked),
                                new SweepTarget(groupB, 2, marked)));
        when(predictions.predictClassGroups(any(), anyInt()))
                .thenReturn(new RunSummary(10, 0, 10, 1));

        SweepSummary summary = service.sweep();

        verify(predictions).predictClassGroups(Set.of(groupA), 1);
        verify(predictions).predictClassGroups(Set.of(groupB), 2);
        assertThat(summary.predicted()).isEqualTo(20);
        assertThat(summary.transitions()).isEqualTo(2);
    }

    /**
     * Clearing before the model answers would drop the very changes the sweep was holding, and
     * nothing anywhere would say a classroom went un-predicted.
     */
    @Test
    @DisplayName("a model outage leaves the marks standing for the next tick")
    void sweep_modelDown_keepsTheQueue() {
        when(queue.pending(BATCH)).thenReturn(List.of(new SweepTarget(groupA, 1, marked)));
        when(predictions.predictClassGroups(any(), eq(1)))
                .thenThrow(new RiskModelUnavailableException("the model is down"));

        SweepSummary summary = service.sweep();

        // Nothing propagates: the only caller is a scheduler, and a thrown sweep would be a stack
        // trace where a sentence about which classrooms are waiting belongs.
        assertThat(summary.predicted()).isZero();
        verify(queue, never()).clearSwept(any(), anyInt(), any());
    }

    /**
     * A save that lands while the model is answering must not be deleted unpredicted. The clear is
     * bounded by the newest mark the sweep actually read, so a later one survives it.
     */
    @Test
    @DisplayName("clears only up to the newest mark it read")
    void sweep_clearsNoFurtherThanWhatItRead() {
        LocalDateTime older = marked;
        LocalDateTime newer = marked.plusMinutes(1);
        List<SweepTarget> taken =
                List.of(new SweepTarget(groupA, 1, older), new SweepTarget(groupB, 1, newer));
        when(queue.pending(BATCH)).thenReturn(taken);
        when(predictions.predictClassGroups(any(), eq(1))).thenReturn(new RunSummary(2, 0, 2, 0));

        service.sweep();

        verify(queue).clearSwept(Set.of(groupA, groupB), 1, newer);
    }

    /**
     * A trimester the model refuses must not cost the others theirs, in either direction: the one
     * that failed keeps its marks for the next tick, and the one that worked does not get swept a
     * second time for having been in the same drain.
     */
    @Test
    @DisplayName("a failed trimester keeps its marks and the successful one is still cleared")
    void sweep_oneTrimesterFails_theOtherIsStillCleared() {
        SweepTarget failing = new SweepTarget(groupA, 1, marked);
        SweepTarget succeeding = new SweepTarget(groupB, 2, marked);
        when(queue.pending(BATCH)).thenReturn(List.of(failing, succeeding));
        when(predictions.predictClassGroups(Set.of(groupA), 1))
                .thenThrow(new RiskModelUnavailableException("rejected"));
        when(predictions.predictClassGroups(Set.of(groupB), 2))
                .thenReturn(new RunSummary(5, 0, 5, 1));

        SweepSummary summary = service.sweep();

        verify(queue).clearSwept(Set.of(groupB), 2, marked);
        verify(queue, never()).clearSwept(Set.of(groupA), 1, marked);
        assertThat(summary.predicted()).isEqualTo(5);
    }
}
