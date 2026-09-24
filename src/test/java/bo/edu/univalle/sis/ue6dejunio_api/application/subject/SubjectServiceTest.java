package bo.edu.univalle.sis.ue6dejunio_api.application.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.subject.SubjectService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.CreateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.UpdateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject.ISubjectDomain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock private ISubjectDomain subjectDomain;
    @InjectMocks private SubjectService subjectService;

    @Test
    void create_defaultsNonTechnical() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.areaExists(4)).thenReturn(true);
        when(subjectDomain.create("Matematicas", 4, false))
                .thenReturn(
                        new Subject(
                                id,
                                "Matematicas",
                                4,
                                "Ciencia Tecnología y Producción",
                                false,
                                true));
        Subject r = subjectService.create(new CreateSubjectCommand("Matematicas", 4, null));
        assertThat(r.technical()).isFalse();
        assertThat(r.active()).isTrue();
    }

    @Test
    void create_technicalTrue() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.areaExists(2)).thenReturn(true);
        when(subjectDomain.create("Musica", 2, true))
                .thenReturn(new Subject(id, "Musica", 2, "Comunidad y Sociedad", true, true));
        Subject r = subjectService.create(new CreateSubjectCommand("Musica", 2, true));
        assertThat(r.technical()).isTrue();
    }

    // Deactivating through update is the same act as deleting, so academic history refuses it the
    // same way. Guarding only delete left the rule open on the other path.
    @Test
    void update_deactivatingASubjectWithScores_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.findById(id))
                .thenReturn(
                        Optional.of(new Subject(id, "X", 2, "Comunidad y Sociedad", false, true)));
        when(subjectDomain.hasScoresForSubject(id)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                subjectService.update(
                                        id, new UpdateSubjectCommand(null, null, null, false)))
                .isInstanceOf(ConflictException.class);
        verify(subjectDomain, org.mockito.Mockito.never())
                .update(any(), any(), any(), any(), any());
    }

    @Test
    void update_reactivating_doesNotConsultScores() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.findById(id))
                .thenReturn(
                        Optional.of(new Subject(id, "X", 2, "Comunidad y Sociedad", false, false)));
        when(subjectDomain.update(id, null, null, null, true))
                .thenReturn(new Subject(id, "X", 2, "Comunidad y Sociedad", false, true));

        assertThat(
                        subjectService
                                .update(id, new UpdateSubjectCommand(null, null, null, true))
                                .active())
                .isTrue();
        verify(subjectDomain, org.mockito.Mockito.never()).hasScoresForSubject(id);
    }

    @Test
    void delete_softDeactivates() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.findById(id))
                .thenReturn(
                        Optional.of(new Subject(id, "X", 2, "Comunidad y Sociedad", false, true)));
        when(subjectDomain.hasScoresForSubject(id)).thenReturn(false);
        subjectService.delete(id);
        verify(subjectDomain).deactivate(id);
    }

    @Test
    void delete_withExistingScores_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.findById(id))
                .thenReturn(
                        Optional.of(new Subject(id, "X", 2, "Comunidad y Sociedad", false, true)));
        when(subjectDomain.hasScoresForSubject(id)).thenReturn(true);
        assertThatThrownBy(() -> subjectService.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessage(
                        "no se pudo desactivar la materia porque tiene calificaciones registradas");
        verify(subjectDomain, org.mockito.Mockito.never()).deactivate(id);
    }

    @Test
    void getById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> subjectService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
