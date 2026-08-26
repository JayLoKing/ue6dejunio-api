package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.StudentMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRepositoryAdapterTest {

    @Mock private JpaStudentRepository repo;
    @Mock private StudentMapper mapper;
    @InjectMocks private StudentRepositoryAdapter adapter;

    // El adaptador se pide en las palabras del dominio y debe aterrizar exactamente en este
    // Pageable: si la traduccion cambiara la pagina o el orden, el repositorio no coincide.
    private final PageQuery pageQuery = PageQuery.of(0, 30);
    private final Pageable pageable = PageRequest.of(0, 30);

    @Test
    void searchDirectory_blankQ_routesToListDirectory() {
        UUID courseId = UUID.randomUUID();
        Page<StudentDirectoryItem> stored = new PageImpl<>(java.util.List.of(), pageable, 0);
        when(repo.listDirectory(eq(courseId), eq(pageable))).thenReturn(stored);

        PageResult<StudentDirectoryItem> result = adapter.searchDirectory("", courseId, pageQuery);

        assertThat(result.content()).isEmpty();
        assertThat(result.size()).isEqualTo(30);
        verify(repo, times(1)).listDirectory(courseId, pageable);
        verify(repo, never()).searchDirectory(any(), any(), any());
    }

    @Test
    void searchDirectory_nullQ_routesToListDirectory() {
        Page<StudentDirectoryItem> stored = new PageImpl<>(java.util.List.of(), pageable, 0);
        when(repo.listDirectory(isNull(), eq(pageable))).thenReturn(stored);

        PageResult<StudentDirectoryItem> result = adapter.searchDirectory(null, null, pageQuery);

        assertThat(result.content()).isEmpty();
        assertThat(result.size()).isEqualTo(30);
        verify(repo, times(1)).listDirectory(null, pageable);
        verify(repo, never()).searchDirectory(any(), any(), any());
    }

    @Test
    void searchDirectory_nonBlankQ_routesToLikeSearch() {
        Page<StudentDirectoryItem> stored = new PageImpl<>(java.util.List.of(), pageable, 0);
        when(repo.searchDirectory(eq("Lopez"), isNull(), eq(pageable))).thenReturn(stored);

        PageResult<StudentDirectoryItem> result = adapter.searchDirectory("Lopez", null, pageQuery);

        assertThat(result.content()).isEmpty();
        assertThat(result.size()).isEqualTo(30);
        verify(repo, times(1)).searchDirectory("Lopez", null, pageable);
        verify(repo, never()).listDirectory(any(), any());
    }

    @Test
    void searchDirectory_whitespaceOnlyQ_routesToListDirectory() {
        Page<StudentDirectoryItem> stored = new PageImpl<>(java.util.List.of(), pageable, 0);
        when(repo.listDirectory(isNull(), eq(pageable))).thenReturn(stored);

        adapter.searchDirectory("   ", null, pageQuery);

        verify(repo, times(1)).listDirectory(null, pageable);
        verify(repo, never()).searchDirectory(any(), any(), any());
    }

    @Test
    void updateStatus_setsStatusAndReason() {
        UUID id = UUID.randomUUID();
        StudentEntity entity = StudentEntity.builder().id(id).status("Effective").build();
        when(repo.findById(id)).thenReturn(Optional.of(entity));

        adapter.updateStatus(id, "Withdrawn", "Retiro Voluntario");

        assertThat(entity.getStatus()).isEqualTo("Withdrawn");
        assertThat(entity.getStatusReason()).isEqualTo("Retiro Voluntario");
        verify(repo).save(entity);
    }

    @Test
    void updateStatus_missingStudent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.updateStatus(id, "Withdrawn", "Otro"))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
