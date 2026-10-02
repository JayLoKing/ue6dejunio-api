package bo.edu.univalle.sis.ue6dejunio_api.application.assessment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment.AssessmentEventService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CreateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.UpdateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssessmentEventServiceTest {

    @Mock private IAssessmentEventDomain eventDomain;
    @Mock private ICriterionDomain criterionDomain;
    @Mock private ClosedYearGuard closedYearGuard;
    @InjectMocks private AssessmentEventService service;

    private final UUID classGroup = UUID.randomUUID();
    private final UUID criterionId = UUID.randomUUID();

    private EvaluationCriterion directCriterion() {
        return new EvaluationCriterion(
                criterionId, classGroup, 1, "Doing", "Participacion", null, null);
    }

    private EvaluationCriterion activityCriterion() {
        return new EvaluationCriterion(
                criterionId,
                classGroup,
                1,
                "Doing",
                "Evaluacion de cuadernos",
                "Revision de Cuadernos",
                null);
    }

    private AssessmentEvent item(UUID id, String title) {
        return new AssessmentEvent(id, criterionId, classGroup, 1, "Doing", title);
    }

    @Test
    void create_underAnActivityCriterion_succeeds() {
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.of(activityCriterion()));

        service.create(new CreateEventCommand(criterionId, "Tema 3"));

        verify(eventDomain).create(criterionId, "Tema 3");
    }

    @Test
    void create_underADirectCriterion_throws() {
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.of(directCriterion()));

        assertThatThrownBy(() -> service.create(new CreateEventCommand(criterionId, "Tema 1")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no pertenece a una actividad");
        verify(eventDomain, never()).create(any(), any());
    }

    @Test
    void create_underAnUnknownCriterion_throws() {
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateEventCommand(criterionId, "Tema 1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_oneOfSeveralItems_succeeds() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.of(item(id, "Tema 1")));
        when(eventDomain.hasScores(id)).thenReturn(false);
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.of(activityCriterion()));
        when(eventDomain.countItems(criterionId)).thenReturn(2L);

        service.delete(id);

        verify(eventDomain).deleteById(id);
    }

    /**
     * Deleting the item cascades its scores away in the database, and nothing recomputes
     * academic_scores: the trimester would keep an average built on rows that no longer exist.
     */
    @Test
    void delete_anItemThatCarriesScores_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.of(item(id, "Tema 1")));
        when(eventDomain.hasScores(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("calificaciones registradas");
        verify(eventDomain, never()).deleteById(any());
    }

    @Test
    void update_withBlankTitle_throws() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.of(item(id, "Tema 1")));

        assertThatThrownBy(() -> service.update(id, new UpdateEventCommand("   ")))
                .isInstanceOf(ValidationException.class);
        verify(eventDomain, never()).update(any(), any());
    }

    @Test
    void update_withNullTitle_isANoOpOnTheName() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.of(item(id, "Tema 1")));

        service.update(id, new UpdateEventCommand(null));
        verify(eventDomain).update(id, null);
    }

    /**
     * An activity criterion with no items left can be scored by no path at all: the direct one is
     * refused because it declares an activity, and there is nothing left to average. It would drop
     * out of its dimension without anyone noticing.
     */
    @Test
    void delete_theLastItemOfAnActivity_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.of(item(id, "Tema unico")));
        when(eventDomain.hasScores(id)).thenReturn(false);
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.of(activityCriterion()));
        when(eventDomain.countItems(criterionId)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Revision de Cuadernos");
        verify(eventDomain, never()).deleteById(any());
    }

    /**
     * A legacy criterion carries items with no activity name. Deleting its last item is allowed: it
     * falls back to being scored directly, which is a reachable state, not a dead one.
     */
    @Test
    void delete_theLastItemOfALegacyCriterion_succeeds() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.of(item(id, "Tema unico")));
        when(eventDomain.hasScores(id)).thenReturn(false);
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.of(directCriterion()));

        service.delete(id);

        verify(eventDomain).deleteById(id);
    }

    @Test
    void delete_unknownItem_throws() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(eventDomain, never()).deleteById(any());
    }

    // ---- closed year: the guard runs before anything is written ----

    private void yearIsClosed() {
        doThrow(new ConflictException("La gestión 2026 está cerrada; no admite cambios."))
                .when(closedYearGuard)
                .requireOpenForClassGroup(classGroup);
    }

    @Test
    void create_inAClosedYear_isRefusedBeforeAnyWrite() {
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.of(activityCriterion()));
        yearIsClosed();

        assertThatThrownBy(() -> service.create(new CreateEventCommand(criterionId, "Tema 3")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(eventDomain, never()).create(any(), any());
    }

    @Test
    void create_asksTheYearBeforeItWrites() {
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.of(activityCriterion()));

        service.create(new CreateEventCommand(criterionId, "Tema 3"));

        InOrder order = inOrder(closedYearGuard, eventDomain);
        order.verify(closedYearGuard).requireOpenForClassGroup(classGroup);
        order.verify(eventDomain).create(criterionId, "Tema 3");
    }

    @Test
    void update_inAClosedYear_isRefusedAndChangesNothing() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.of(item(id, "Tema 1")));
        yearIsClosed();

        assertThatThrownBy(() -> service.update(id, new UpdateEventCommand("Otro")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(eventDomain, never()).update(any(), any());
    }

    @Test
    void delete_inAClosedYear_isRefusedAndRemovesNothing() {
        UUID id = UUID.randomUUID();
        when(eventDomain.findById(id)).thenReturn(Optional.of(item(id, "Tema 1")));
        yearIsClosed();

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(eventDomain, never()).deleteById(any());
    }

    @Test
    void create_underAnUnknownCriterion_keepsItsOwn404() {
        when(criterionDomain.findById(criterionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateEventCommand(criterionId, "Tema 3")))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(closedYearGuard);
    }
}
