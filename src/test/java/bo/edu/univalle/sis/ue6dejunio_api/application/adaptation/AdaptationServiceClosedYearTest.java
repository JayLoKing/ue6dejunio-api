package bo.edu.univalle.sis.ue6dejunio_api.application.adaptation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.adaptation.AdaptationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationDomain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * An adaptation belongs to a plan, and the plan to a course: the year is asked through the plan.
 */
@ExtendWith(MockitoExtension.class)
class AdaptationServiceClosedYearTest {

    @Mock private IAdaptationDomain adaptationDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    private final UUID planId = UUID.randomUUID();
    private final UUID adaptationId = UUID.randomUUID();

    private AdaptationService service() {
        return new AdaptationService(adaptationDomain, closedYearGuard);
    }

    private void planIsClosed() {
        doThrow(new ConflictException("La gestión 2026 está cerrada; no admite cambios."))
                .when(closedYearGuard)
                .requireOpenForPlan(planId);
    }

    private Adaptation adaptation() {
        return new Adaptation(
                adaptationId,
                planId,
                UUID.randomUUID(),
                null,
                "Visual",
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void create_inAClosedYear_isRefusedBeforeAnyWrite() {
        when(adaptationDomain.planExists(planId)).thenReturn(true);
        planIsClosed();
        CreateAdaptationCommand command =
                new CreateAdaptationCommand(
                        planId, UUID.randomUUID(), "Visual", null, null, null, UUID.randomUUID());

        assertThatThrownBy(() -> service().create(command))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(adaptationDomain, never()).create(any());
    }

    @Test
    void update_inAClosedYear_isRefusedAndChangesNothing() {
        when(adaptationDomain.findById(adaptationId)).thenReturn(Optional.of(adaptation()));
        planIsClosed();
        UpdateAdaptationCommand command =
                new UpdateAdaptationCommand("Auditiva", null, null, null, null);

        assertThatThrownBy(() -> service().update(adaptationId, command))
                .isInstanceOf(ConflictException.class);

        verify(adaptationDomain, never()).update(any(), any());
    }

    @Test
    void delete_inAClosedYear_isRefusedAndRemovesNothing() {
        when(adaptationDomain.findById(adaptationId)).thenReturn(Optional.of(adaptation()));
        planIsClosed();

        assertThatThrownBy(() -> service().delete(adaptationId))
                .isInstanceOf(ConflictException.class);

        verify(adaptationDomain, never()).deleteById(any());
    }
}
