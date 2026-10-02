package bo.edu.univalle.sis.ue6dejunio_api.application.progress;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.progress.ProgressService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.CreateProgressCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressDomain;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgressServiceClosedYearTest {

    @Mock private IProgressDomain progressDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    @Test
    void create_inAClosedYear_isRefusedBeforeAnyWrite() {
        UUID planId = UUID.randomUUID();
        when(progressDomain.planExists(planId)).thenReturn(true);
        doThrow(new ConflictException("La gestión 2026 está cerrada; no admite cambios."))
                .when(closedYearGuard)
                .requireOpenForPlan(planId);
        CreateProgressCommand command =
                new CreateProgressCommand(
                        planId,
                        LocalDate.of(2026, 3, 1),
                        "Tema 1",
                        BigDecimal.TEN,
                        null,
                        UUID.randomUUID());

        assertThatThrownBy(
                        () -> new ProgressService(progressDomain, closedYearGuard).create(command))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(progressDomain, never()).create(any(), any(), any(), any(), any(), any());
    }
}
