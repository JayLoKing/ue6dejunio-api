package bo.edu.univalle.sis.ue6dejunio_api.application.risk;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.risk.RiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskModelClient;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Marking a prediction attended is a teacher's own entry and freezes with the year. Running a
 * prediction derives data from the record, so it is not guarded and is not tested here.
 */
@ExtendWith(MockitoExtension.class)
class RiskPredictionServiceClosedYearTest {

    @Mock private IRiskFeatureDomain featureDomain;
    @Mock private IRiskModelClient modelClient;
    @Mock private IRiskPredictionDomain predictionDomain;
    @Mock private INotificationService notifications;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private ICourseService courseService;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    @Test
    void markAttended_inAClosedYear_isRefusedBeforeThePredictionIsTouched() {
        UUID predictionId = UUID.randomUUID();
        doThrow(new ConflictException("La gestión 2026 está cerrada; no admite cambios."))
                .when(closedYearGuard)
                .requireOpenForRiskPrediction(predictionId);
        RiskPredictionService service =
                new RiskPredictionService(
                        featureDomain,
                        modelClient,
                        predictionDomain,
                        notifications,
                        classGroupDomain,
                        courseService,
                        enrollmentDomain,
                        Clock.systemDefaultZone(),
                        closedYearGuard);

        assertThatThrownBy(() -> service.markAttended(predictionId, true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(predictionDomain, never()).markAttended(predictionId, true);
        verifyNoInteractions(modelClient);
    }
}
