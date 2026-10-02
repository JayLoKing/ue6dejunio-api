package bo.edu.univalle.sis.ue6dejunio_api.application.academicyear;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearLockDomain;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The one place that decides whether a write may land on a year. The port answers which closed
 * years a target belongs to; an unknown target belongs to none, so the guard passes it through and
 * the use case keeps its own 404.
 */
@ExtendWith(MockitoExtension.class)
class ClosedYearGuardTest {

    @Mock private IAcademicYearLockDomain lock;
    @Mock private IAcademicYearDomain years;

    private ClosedYearGuard guard;

    private final UUID id = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        guard = new ClosedYearGuard(lock, years);
    }

    @Test
    void classGroup_ofAnOpenYear_passes() {
        when(lock.closedYearsOfClassGroups(Set.of(id))).thenReturn(Set.of());

        assertThatCode(() -> guard.requireOpenForClassGroup(id)).doesNotThrowAnyException();
    }

    @Test
    void classGroup_ofAClosedYear_isAConflictNamingTheYear() {
        when(lock.closedYearsOfClassGroups(Set.of(id))).thenReturn(Set.of(2026));

        assertThatThrownBy(() -> guard.requireOpenForClassGroup(id))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La gestión 2026 está cerrada; no admite cambios.");
    }

    @Test
    void classGroup_unknownTarget_passesSoTheUseCaseKeepsItsOwn404() {
        // An id that resolves to nothing has no year at all, so the port answers an empty set.
        when(lock.closedYearsOfClassGroups(Set.of(id))).thenReturn(Set.of());

        assertThatCode(() -> guard.requireOpenForClassGroup(id)).doesNotThrowAnyException();
    }

    @Test
    void plan_ofAClosedYear_isRefused_andAnOpenOneAndAnUnknownOnePass() {
        when(lock.closedYearsOfPlans(Set.of(id))).thenReturn(Set.of(2026));
        when(lock.closedYearsOfPlans(Set.of(other))).thenReturn(Set.of());

        assertThatThrownBy(() -> guard.requireOpenForPlan(id))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La gestión 2026 está cerrada; no admite cambios.");
        assertThatCode(() -> guard.requireOpenForPlan(other)).doesNotThrowAnyException();
    }

    @Test
    void riskPrediction_ofAClosedYear_isRefused_andAnOpenOneAndAnUnknownOnePass() {
        when(lock.closedYearsOfRiskPredictions(Set.of(id))).thenReturn(Set.of(2025));
        when(lock.closedYearsOfRiskPredictions(Set.of(other))).thenReturn(Set.of());

        assertThatThrownBy(() -> guard.requireOpenForRiskPrediction(id))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La gestión 2025 está cerrada; no admite cambios.");
        assertThatCode(() -> guard.requireOpenForRiskPrediction(other)).doesNotThrowAnyException();
    }

    @Test
    void classGroups_aBatchIsOneQueryNoMatterHowManyIds() {
        when(lock.closedYearsOfClassGroups(Set.of(id, other))).thenReturn(Set.of());

        guard.requireOpenForClassGroups(List.of(id, other, id));

        verify(lock).closedYearsOfClassGroups(Set.of(id, other));
        verifyNoMoreInteractions(lock);
    }

    @Test
    void classGroups_severalClosedYears_areAllNamedInOrder() {
        when(lock.closedYearsOfClassGroups(Set.of(id, other))).thenReturn(Set.of(2026, 2025));

        assertThatThrownBy(() -> guard.requireOpenForClassGroups(List.of(id, other)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Las gestiones 2025, 2026 están cerradas; no admiten cambios.");
    }

    @Test
    void emptyBatch_asksNothingAndPasses() {
        guard.requireOpenForEnrollments(List.of());

        verifyNoMoreInteractions(lock, years);
    }

    @Test
    void enrollment_ofAClosedYear_isAConflict() {
        when(lock.closedYearsOfEnrollments(Set.of(id))).thenReturn(Set.of(2025));

        assertThatThrownBy(() -> guard.requireOpenForEnrollment(id))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La gestión 2025 está cerrada; no admite cambios.");
    }

    @Test
    void enrollments_ofAnOpenYear_pass() {
        when(lock.closedYearsOfEnrollments(Set.of(id, other))).thenReturn(Set.of());

        assertThatCode(() -> guard.requireOpenForEnrollments(List.of(id, other)))
                .doesNotThrowAnyException();
    }

    @Test
    void course_ofAClosedYear_isAConflict() {
        when(lock.closedYearsOfCourses(Set.of(id))).thenReturn(Set.of(2026));

        assertThatThrownBy(() -> guard.requireOpenForCourse(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2026");
    }

    @Test
    void course_ofAnOpenYear_passes() {
        when(lock.closedYearsOfCourses(Set.of(id))).thenReturn(Set.of());

        assertThatCode(() -> guard.requireOpenForCourse(id)).doesNotThrowAnyException();
    }

    @Test
    void year_closed_isAConflictNamingTheYear() {
        when(years.findItem(7)).thenReturn(Optional.of(new AcademicYearItem(7, 2026, false, true)));

        assertThatThrownBy(() -> guard.requireOpenForYear(7))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La gestión 2026 está cerrada; no admite cambios.");
    }

    @Test
    void year_open_passes() {
        when(years.findItem(7)).thenReturn(Optional.of(new AcademicYearItem(7, 2026, true, false)));

        assertThatCode(() -> guard.requireOpenForYear(7)).doesNotThrowAnyException();
    }

    @Test
    void year_unknown_passesSoTheCallerKeepsItsOwn404() {
        when(years.findItem(9)).thenReturn(Optional.empty());

        assertThatCode(() -> guard.requireOpenForYear(9)).doesNotThrowAnyException();
    }
}
