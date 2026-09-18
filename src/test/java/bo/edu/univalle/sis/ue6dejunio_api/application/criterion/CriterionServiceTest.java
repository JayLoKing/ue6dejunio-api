package bo.edu.univalle.sis.ue6dejunio_api.application.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.criterion.CriterionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.CreateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.UpdateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskInputsChanged;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriterionServiceTest {

    @Mock private ICriterionDomain criterionDomain;
    @Mock private IAssessmentEventDomain eventDomain;
    @Mock private IDomainEventPublisher events;
    @InjectMocks private CriterionService criterionService;

    private final UUID classGroupId = UUID.randomUUID();

    private EvaluationCriterion criterion(UUID id, String name, String activityName) {
        return new EvaluationCriterion(id, classGroupId, 1, "Doing", name, activityName, null);
    }

    private CreateCriterionCommand command(String name, String activityName, List<String> items) {
        return new CreateCriterionCommand(classGroupId, 1, "Doing", name, activityName, items, null);
    }

    @Test
    void create_withoutActivity_createsADirectCriterionAndNoItems() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.classGroupExists(classGroupId)).thenReturn(true);
        when(criterionDomain.create(classGroupId, 1, "Doing", "Participacion", null, null))
            .thenReturn(criterion(id, "Participacion", null));

        EvaluationCriterion created = criterionService.create(
            command("Participacion", null, null));

        assertThat(created.isActivityBased()).isFalse();
        verifyNoInteractions(eventDomain);
    }

    /**
     * Planning a criterion moves a model input without a mark changing: progress is marks over what
     * was planned, and three of three and three of seven are the same count meaning opposite things.
     */
    @Test
    void create_saysTheModelsInputsChanged() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.classGroupExists(classGroupId)).thenReturn(true);
        when(criterionDomain.create(classGroupId, 1, "Doing", "Participacion", null, null))
            .thenReturn(criterion(id, "Participacion", null));

        criterionService.create(command("Participacion", null, null));

        verify(events).publish(new RiskInputsChanged(classGroupId, 1));
    }

    /** Removing one moves the same denominator the other way. */
    @Test
    void delete_saysTheModelsInputsChanged() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.findById(id)).thenReturn(Optional.of(criterion(id, "Participacion", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(false);

        criterionService.delete(id);

        verify(events).publish(new RiskInputsChanged(classGroupId, 1));
    }

    @Test
    void create_withActivity_createsTheCriterionThenItsItemsInOrder() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.classGroupExists(classGroupId)).thenReturn(true);
        when(criterionDomain.create(classGroupId, 1, "Doing", "Evaluacion de cuadernos",
            "Revision de Cuadernos", null))
            .thenReturn(criterion(id, "Evaluacion de cuadernos", "Revision de Cuadernos"));

        criterionService.create(command("Evaluacion de cuadernos", "Revision de Cuadernos",
            List.of("Tema 1", "Tema 2", "Tema 3")));

        InOrder order = inOrder(criterionDomain, eventDomain);
        order.verify(criterionDomain).create(any(), any(), any(), any(), any(), any());
        order.verify(eventDomain).create(id, "Tema 1");
        order.verify(eventDomain).create(id, "Tema 2");
        order.verify(eventDomain).create(id, "Tema 3");
    }

    @Test
    void create_withActivityAndNoItems_throwsAndPersistsNothing() {
        when(criterionDomain.classGroupExists(classGroupId)).thenReturn(true);

        assertThatThrownBy(() -> criterionService.create(
            command("Evaluacion de cuadernos", "Revision de Cuadernos", List.of())))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("al menos un criterio");
        verify(criterionDomain, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_withItemsButNoActivityName_throws() {
        when(criterionDomain.classGroupExists(classGroupId)).thenReturn(true);

        assertThatThrownBy(() -> criterionService.create(
            command("Participacion", null, List.of("Tema 1"))))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("nombre de la actividad");
        verify(criterionDomain, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_withRepeatedItems_throws() {
        when(criterionDomain.classGroupExists(classGroupId)).thenReturn(true);

        assertThatThrownBy(() -> criterionService.create(
            command("Evaluacion", "Revision", List.of("Tema 1", "tema 1"))))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("duplicado");
    }

    @Test
    void create_withBlankItems_dropsThemInsteadOfCreatingEmptyBoxes() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.classGroupExists(classGroupId)).thenReturn(true);
        when(criterionDomain.create(eq(classGroupId), eq(1), eq("Doing"), eq("Evaluacion"),
            eq("Revision"), any()))
            .thenReturn(criterion(id, "Evaluacion", "Revision"));

        criterionService.create(command("Evaluacion", "Revision", List.of("Tema 1", "   ", "Tema 2")));

        verify(eventDomain).create(id, "Tema 1");
        verify(eventDomain).create(id, "Tema 2");
        verify(eventDomain, never()).create(eq(id), eq("   "));
    }

    @Test
    void create_withUnknownClassGroup_throws() {
        when(criterionDomain.classGroupExists(classGroupId)).thenReturn(false);

        assertThatThrownBy(() -> criterionService.create(command("Participacion", null, null)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withInvalidDimension_throws() {
        CreateCriterionCommand invalid = new CreateCriterionCommand(
            classGroupId, 1, "COGNITIVA", "Participacion", null, null, null);

        assertThatThrownBy(() -> criterionService.create(invalid))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Dimension invalida");
    }

    @Test
    void update_withoutScores_succeeds() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.findById(id)).thenReturn(Optional.of(criterion(id, "Old", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(false);
        when(criterionDomain.update(id, "New")).thenReturn(criterion(id, "New", null));

        EvaluationCriterion r = criterionService.update(id, new UpdateCriterionCommand("New"));

        assertThat(r.name()).isEqualTo("New");
    }

    @Test
    void update_withExistingScores_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.findById(id)).thenReturn(Optional.of(criterion(id, "Old", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(true);

        assertThatThrownBy(() -> criterionService.update(id, new UpdateCriterionCommand("New")))
            .isInstanceOf(ConflictException.class)
            .hasMessage("El criterio no puede modificarse porque ya cuenta con calificaciones registradas");
        verify(criterionDomain, never()).update(id, "New");
    }

    @Test
    void delete_withoutScores_succeeds() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.findById(id)).thenReturn(Optional.of(criterion(id, "Old", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(false);

        criterionService.delete(id);

        verify(criterionDomain).deleteById(id);
    }

    @Test
    void delete_withExistingScores_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.findById(id)).thenReturn(Optional.of(criterion(id, "Old", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(true);

        assertThatThrownBy(() -> criterionService.delete(id))
            .isInstanceOf(ConflictException.class)
            .hasMessage("El criterio no puede eliminarse porque ya cuenta con calificaciones registradas");
        verify(criterionDomain, never()).deleteById(id);
    }

    @Test
    void getById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(criterionDomain.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> criterionService.getById(id))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
