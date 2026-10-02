package bo.edu.univalle.sis.ue6dejunio_api.application.pdc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.pdc.PdcService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A plan of a closed year is refused before anything is written, whatever the move. */
@ExtendWith(MockitoExtension.class)
class PdcServiceClosedYearTest {

    @Mock private IPdcDomain pdcDomain;
    @Mock private IDomainEventPublisher events;
    @Mock private ClosedYearGuard closedYearGuard;

    private final UUID user = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    private PdcService service() {
        return new PdcService(pdcDomain, events, closedYearGuard);
    }

    private static ConflictException closed() {
        return new ConflictException("La gestión 2026 está cerrada; no admite cambios.");
    }

    private Pdc plan(String status) {
        return Pdc.builder()
                .id(planId)
                .courseId(courseId)
                .status(status)
                .planNumber(1)
                .trimester(1)
                .periodStart(LocalDate.of(2026, 2, 1))
                .periodEnd(LocalDate.of(2026, 2, 28))
                .build();
    }

    @Test
    void create_inAClosedYear_isRefusedBeforeAnyWrite() {
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(courseId);
        CreatePdcCommand command =
                new CreatePdcCommand(
                        courseId,
                        1,
                        1,
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> service().create(command, user))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(pdcDomain, never()).save(any());
    }

    @Test
    void update_inAClosedYear_isRefusedAndChangesNothing() {
        when(pdcDomain.findById(planId)).thenReturn(Optional.of(plan(PdcStatus.DRAFT)));
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(courseId);
        UpdatePdcCommand command = new UpdatePdcCommand(null, null, null, "Otro", null, null);

        assertThatThrownBy(() -> service().update(planId, command, user))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cerrada");

        verify(pdcDomain, never()).save(any());
    }

    @Test
    void writeSubject_isRefusedThroughThePlan_withoutLoadingItWhole() {
        doThrow(closed()).when(closedYearGuard).requireOpenForPlan(planId);
        UpsertPdcSubjectCommand command = new UpsertPdcSubjectCommand("Leer", null, null);
        UUID block = UUID.randomUUID();

        assertThatThrownBy(() -> service().writeSubject(planId, block, command, user))
                .isInstanceOf(ConflictException.class);

        verify(pdcDomain, never()).statusOf(any());
        verify(pdcDomain, never()).writeSubject(any(), any(), any(), any());
    }

    @Test
    void copyToSiblingCourses_asksOnceForEveryTargetCourse() {
        UUID sibling = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(pdcDomain.findById(planId)).thenReturn(Optional.of(plan(PdcStatus.PUBLISHED)));
        when(pdcDomain.siblingCourseIdsOf(courseId)).thenReturn(List.of(sibling, other));
        when(pdcDomain.courseIdsWithPlan(List.of(sibling, other), 1, 1)).thenReturn(Set.of());
        doThrow(closed()).when(closedYearGuard).requireOpenForCourses(List.of(sibling, other));

        assertThatThrownBy(() -> service().copyToSiblingCourses(planId, user))
                .isInstanceOf(ConflictException.class);

        verify(pdcDomain, never()).copyTo(any(), any(), any());
    }

    @Test
    void publish_inAClosedYear_isRefused() {
        when(pdcDomain.findById(planId)).thenReturn(Optional.of(plan(PdcStatus.DRAFT)));
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(courseId);

        assertThatThrownBy(() -> service().publish(planId, user))
                .isInstanceOf(ConflictException.class);

        verify(pdcDomain, never()).save(any());
    }

    @Test
    void approve_inAClosedYear_isRefused() {
        when(pdcDomain.findById(planId)).thenReturn(Optional.of(plan(PdcStatus.PUBLISHED)));
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(courseId);

        assertThatThrownBy(() -> service().approve(planId, user))
                .isInstanceOf(ConflictException.class);

        verify(pdcDomain, never()).save(any());
    }

    @Test
    void observe_inAClosedYear_isRefused() {
        when(pdcDomain.findById(planId)).thenReturn(Optional.of(plan(PdcStatus.PUBLISHED)));
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(courseId);

        assertThatThrownBy(() -> service().observe(planId, "Corregir", user))
                .isInstanceOf(ConflictException.class);

        verify(pdcDomain, never()).save(any());
    }

    @Test
    void delete_inAClosedYear_isRefused() {
        when(pdcDomain.findById(planId)).thenReturn(Optional.of(plan(PdcStatus.DRAFT)));
        doThrow(closed()).when(closedYearGuard).requireOpenForCourse(courseId);

        assertThatThrownBy(() -> service().delete(planId)).isInstanceOf(ConflictException.class);

        verify(pdcDomain, never()).deleteById(any());
    }
}
