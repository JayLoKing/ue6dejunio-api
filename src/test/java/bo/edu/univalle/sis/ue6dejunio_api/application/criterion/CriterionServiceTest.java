package bo.edu.univalle.sis.ue6dejunio_api.application.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.criterion.CriterionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.UpdateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriterionServiceTest {

    @Mock private ICriterionDomain criterionDomain;
    @InjectMocks private CriterionService criterionService;

    @Test
    void update_withoutScores_succeeds() {
        UUID id = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(criterionDomain.findById(id))
            .thenReturn(Optional.of(new EvaluationCriterion(id, classGroupId, 1, "COGNITIVA", "Old", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(false);
        when(criterionDomain.update(id, "New"))
            .thenReturn(new EvaluationCriterion(id, classGroupId, 1, "COGNITIVA", "New", null));

        EvaluationCriterion r = criterionService.update(id, new UpdateCriterionCommand("New"));

        assertThat(r.name()).isEqualTo("New");
    }

    @Test
    void update_withExistingScores_throwsConflict() {
        UUID id = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(criterionDomain.findById(id))
            .thenReturn(Optional.of(new EvaluationCriterion(id, classGroupId, 1, "COGNITIVA", "Old", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(true);

        assertThatThrownBy(() -> criterionService.update(id, new UpdateCriterionCommand("New")))
            .isInstanceOf(ConflictException.class)
            .hasMessage("el criterio no puede modificarse porque ya cuenta con calificaciones registradas");
        verify(criterionDomain, never()).update(id, "New");
    }

    @Test
    void delete_withoutScores_succeeds() {
        UUID id = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(criterionDomain.findById(id))
            .thenReturn(Optional.of(new EvaluationCriterion(id, classGroupId, 1, "COGNITIVA", "Old", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(false);

        criterionService.delete(id);

        verify(criterionDomain).deleteById(id);
    }

    @Test
    void delete_withExistingScores_throwsConflict() {
        UUID id = UUID.randomUUID();
        UUID classGroupId = UUID.randomUUID();
        when(criterionDomain.findById(id))
            .thenReturn(Optional.of(new EvaluationCriterion(id, classGroupId, 1, "COGNITIVA", "Old", null)));
        when(criterionDomain.hasScoresForCriterion(id)).thenReturn(true);

        assertThatThrownBy(() -> criterionService.delete(id))
            .isInstanceOf(ConflictException.class)
            .hasMessage("el criterio no puede modificarse porque ya cuenta con calificaciones registradas");
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
