package bo.edu.univalle.sis.ue6dejunio_api.application.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.progress.ProgressService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.CreateProgressCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressDomain;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock private IProgressDomain progressDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    private final UUID planId = UUID.randomUUID();

    private CreateProgressCommand command() {
        return new CreateProgressCommand(
                planId,
                LocalDate.of(2026, 3, 1),
                "Tema 1",
                BigDecimal.TEN,
                null,
                UUID.randomUUID());
    }

    private ProgressService service() {
        return new ProgressService(progressDomain, closedYearGuard);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Draft", "Published", "Under Review", "With Observations"})
    void create_onAPlanThatIsNotApproved_isRefusedBeforeAnyWrite(String status) {
        when(progressDomain.findPlanStatus(planId)).thenReturn(Optional.of(status));

        assertThatThrownBy(() -> service().create(command()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("aprobado");

        verify(progressDomain, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_onAnApprovedPlan_writesTheProgress() {
        when(progressDomain.findPlanStatus(planId)).thenReturn(Optional.of(PdcStatus.APPROVED));
        PlanProgress saved = org.mockito.Mockito.mock(PlanProgress.class);
        when(progressDomain.create(any(), any(), any(), any(), any(), any())).thenReturn(saved);

        assertThat(service().create(command())).isSameAs(saved);

        verify(closedYearGuard).requireOpenForPlan(planId);
    }

    @Test
    void create_onAMissingPlan_isNotFound() {
        when(progressDomain.findPlanStatus(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(command()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(progressDomain, never()).create(any(), any(), any(), any(), any(), any());
    }
}
