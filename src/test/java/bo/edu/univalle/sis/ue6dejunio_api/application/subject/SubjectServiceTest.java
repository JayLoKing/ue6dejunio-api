package bo.edu.univalle.sis.ue6dejunio_api.application.subject;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.subject.SubjectService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.CreateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject.ISubjectDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock private ISubjectDomain subjectDomain;
    @InjectMocks private SubjectService subjectService;

    @Test
    void create_defaultsNonTechnical() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.create("Matematicas", false))
            .thenReturn(new Subject(id, "Matematicas", false, true));
        Subject r = subjectService.create(new CreateSubjectCommand("Matematicas", null));
        assertThat(r.technical()).isFalse();
        assertThat(r.active()).isTrue();
    }

    @Test
    void create_technicalTrue() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.create("Musica", true))
            .thenReturn(new Subject(id, "Musica", true, true));
        Subject r = subjectService.create(new CreateSubjectCommand("Musica", true));
        assertThat(r.technical()).isTrue();
    }

    @Test
    void delete_softDeactivates() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.findById(id)).thenReturn(Optional.of(new Subject(id, "X", false, true)));
        when(subjectDomain.hasScoresForSubject(id)).thenReturn(false);
        subjectService.delete(id);
        verify(subjectDomain).deactivate(id);
    }

    @Test
    void delete_withExistingScores_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(subjectDomain.findById(id)).thenReturn(Optional.of(new Subject(id, "X", false, true)));
        when(subjectDomain.hasScoresForSubject(id)).thenReturn(true);
        assertThatThrownBy(() -> subjectService.delete(id))
            .isInstanceOf(ConflictException.class)
            .hasMessage("no se pudo desactivar la materia porque tiene calificaciones registradas");
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
