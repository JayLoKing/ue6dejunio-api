package bo.edu.univalle.sis.ue6dejunio_api.application.score;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.score.ScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreByStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock private IScoreDomain scoreDomain;
    @Mock private IEnrollmentDomain enrollmentDomain;
    @InjectMocks private ScoreService scoreService;

    private RegisterScoreCommand cmd(UUID enrollmentId) {
        return new RegisterScoreCommand(enrollmentId, 1,
            new BigDecimal("8"), new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("4"),
            UUID.randomUUID());
    }

    private AcademicScore mockScore(UUID enr) {
        return new AcademicScore(UUID.randomUUID(), enr, 1,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            UUID.randomUUID(), null);
    }

    @Test
    void register_enrollmentExists_upserts() {
        UUID enr = UUID.randomUUID();
        when(scoreDomain.enrollmentExists(enr)).thenReturn(true);
        when(scoreDomain.upsert(any())).thenReturn(mockScore(enr));
        AcademicScore r = scoreService.register(cmd(enr));
        assertThat(r.enrollmentId()).isEqualTo(enr);
    }

    @Test
    void register_enrollmentMissing_throws() {
        UUID enr = UUID.randomUUID();
        when(scoreDomain.enrollmentExists(enr)).thenReturn(false);
        assertThatThrownBy(() -> scoreService.register(cmd(enr)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registerByStudent_resolvesEnrollment() {
        UUID studentId = UUID.randomUUID();
        UUID cgId = UUID.randomUUID();
        UUID enrId = UUID.randomUUID();
        when(enrollmentDomain.findEnrollmentId(studentId, cgId)).thenReturn(Optional.of(enrId));
        when(scoreDomain.upsert(any())).thenReturn(mockScore(enrId));

        scoreService.registerByStudent(new RegisterScoreByStudentCommand(
            studentId, cgId, 2, new BigDecimal("9"), new BigDecimal("44"),
            new BigDecimal("39"), new BigDecimal("5"), UUID.randomUUID()));

        ArgumentCaptor<RegisterScoreCommand> cap = ArgumentCaptor.forClass(RegisterScoreCommand.class);
        verify(scoreDomain).upsert(cap.capture());
        assertThat(cap.getValue().enrollmentId()).isEqualTo(enrId);
        assertThat(cap.getValue().trimester()).isEqualTo(2);
    }

    @Test
    void registerByStudent_noEnrollment_throws() {
        UUID studentId = UUID.randomUUID();
        UUID cgId = UUID.randomUUID();
        when(enrollmentDomain.findEnrollmentId(studentId, cgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> scoreService.registerByStudent(new RegisterScoreByStudentCommand(
            studentId, cgId, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            UUID.randomUUID())))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
