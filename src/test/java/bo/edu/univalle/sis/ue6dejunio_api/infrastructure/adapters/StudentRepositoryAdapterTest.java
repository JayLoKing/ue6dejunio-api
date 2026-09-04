package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryScope;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.StudentMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
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
    @Mock private JpaUserRepository userRepo;
    @Mock private StudentMapper mapper;
    @InjectMocks private StudentRepositoryAdapter adapter;

    // El adaptador se pide en las palabras del dominio y debe aterrizar exactamente en este
    // Pageable: si la traduccion cambiara la pagina o el orden, el repositorio no coincide.
    private final PageQuery pageQuery = PageQuery.of(0, 30);
    private final Pageable pageable = PageRequest.of(0, 30);

    private static final String ACTIVE = "Effective";

    private static Page<StudentDirectoryItem> emptyPage(Pageable pageable) {
        return new PageImpl<>(java.util.List.of(), pageable, 0);
    }

    @Test
    void searchDirectory_blankQ_routesToListDirectory() {
        UUID courseId = UUID.randomUUID();
        when(repo.listDirectory(eq(courseId), isNull(), isNull(), isNull(), eq(ACTIVE),
            eq(pageable))).thenReturn(emptyPage(pageable));

        PageResult<StudentDirectoryItem> result = adapter.searchDirectory(
            StudentDirectoryQuery.of("", courseId), pageQuery);

        assertThat(result.content()).isEmpty();
        assertThat(result.size()).isEqualTo(30);
        verify(repo, times(1)).listDirectory(courseId, null, null, null, ACTIVE, pageable);
        verify(repo, never()).searchDirectory(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchDirectory_nullQ_routesToListDirectory() {
        when(repo.listDirectory(isNull(), isNull(), isNull(), isNull(), eq(ACTIVE), eq(pageable)))
            .thenReturn(emptyPage(pageable));

        PageResult<StudentDirectoryItem> result = adapter.searchDirectory(
            StudentDirectoryQuery.of(null, null), pageQuery);

        assertThat(result.content()).isEmpty();
        assertThat(result.size()).isEqualTo(30);
        verify(repo, times(1)).listDirectory(null, null, null, null, ACTIVE, pageable);
        verify(repo, never()).searchDirectory(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchDirectory_nonBlankQ_routesToLikeSearch() {
        when(repo.searchDirectory(eq("Lopez"), isNull(), isNull(), isNull(), isNull(), eq(ACTIVE),
            eq(pageable))).thenReturn(emptyPage(pageable));

        PageResult<StudentDirectoryItem> result = adapter.searchDirectory(
            StudentDirectoryQuery.of("Lopez", null), pageQuery);

        assertThat(result.content()).isEmpty();
        assertThat(result.size()).isEqualTo(30);
        verify(repo, times(1)).searchDirectory("Lopez", null, null, null, null, ACTIVE, pageable);
        verify(repo, never()).listDirectory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchDirectory_whitespaceOnlyQ_routesToListDirectory() {
        when(repo.listDirectory(isNull(), isNull(), isNull(), isNull(), eq(ACTIVE), eq(pageable)))
            .thenReturn(emptyPage(pageable));

        adapter.searchDirectory(StudentDirectoryQuery.of("   ", null), pageQuery);

        verify(repo, times(1)).listDirectory(null, null, null, null, ACTIVE, pageable);
        verify(repo, never()).searchDirectory(any(), any(), any(), any(), any(), any(), any());
    }

    /** Typing spaces around a surname is typing, not part of the surname. */
    @Test
    void searchDirectory_trimsWhatWasTyped() {
        when(repo.searchDirectory(eq("Lopez"), isNull(), isNull(), isNull(), isNull(), eq(ACTIVE),
            eq(pageable))).thenReturn(emptyPage(pageable));

        adapter.searchDirectory(StudentDirectoryQuery.of("  Lopez  ", null), pageQuery);

        verify(repo, times(1)).searchDirectory("Lopez", null, null, null, null, ACTIVE, pageable);
    }

    /** ALL narrows to nothing: a null status is how the query says "whatever they are now". */
    @Test
    void searchDirectory_spanningEverybody_bindsNoStatus() {
        when(repo.listDirectory(isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
            .thenReturn(emptyPage(pageable));

        adapter.searchDirectory(new StudentDirectoryQuery(
            null, null, null, null, null, StudentDirectoryScope.ALL), pageQuery);

        verify(repo, times(1)).listDirectory(null, null, null, null, null, pageable);
    }

    @Test
    void searchDirectory_gradeAndParallel_travelToTheQuery() {
        when(repo.listDirectory(isNull(), eq(3), eq(2), isNull(), eq("Withdrawn"), eq(pageable)))
            .thenReturn(emptyPage(pageable));

        adapter.searchDirectory(new StudentDirectoryQuery(
            null, null, 3, 2, null, StudentDirectoryScope.WITHDRAWN), pageQuery);

        verify(repo, times(1)).listDirectory(null, 3, 2, null, "Withdrawn", pageable);
    }

    /** The gestión reaches both queries: the listing one and the one behind the search box. */
    @Test
    void searchDirectory_gestion_travelsToTheListQuery() {
        when(repo.listDirectory(isNull(), isNull(), isNull(), eq(5), eq(ACTIVE), eq(pageable)))
            .thenReturn(emptyPage(pageable));

        adapter.searchDirectory(new StudentDirectoryQuery(
            null, null, null, null, 5, StudentDirectoryScope.ACTIVE), pageQuery);

        verify(repo, times(1)).listDirectory(null, null, null, 5, ACTIVE, pageable);
    }

    @Test
    void searchDirectory_gestion_travelsToTheSearchQuery() {
        when(repo.searchDirectory(eq("Lopez"), isNull(), isNull(), isNull(), eq(5), eq(ACTIVE),
            eq(pageable))).thenReturn(emptyPage(pageable));

        adapter.searchDirectory(new StudentDirectoryQuery(
            "Lopez", null, null, null, 5, StudentDirectoryScope.ACTIVE), pageQuery);

        verify(repo, times(1)).searchDirectory("Lopez", null, null, null, 5, ACTIVE, pageable);
    }

    @Test
    void updateStatus_setsStatusAndReason() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        StudentEntity entity = StudentEntity.builder().id(id).status("Effective").build();
        when(repo.findById(id)).thenReturn(Optional.of(entity));
        when(userRepo.getReferenceById(director)).thenReturn(new UserEntity());

        adapter.updateStatus(id, new StudentStatusChange(
            "Withdrawn", "Retiro Voluntario", null, director));

        assertThat(entity.getStatus()).isEqualTo("Withdrawn");
        assertThat(entity.getStatusReason()).isEqualTo("Retiro Voluntario");
        verify(repo).save(entity);
    }

    /** The Director's own words, for the category that says nothing without them. */
    @Test
    void updateStatus_carriesTheNoteAndWhoDecided() {
        UUID id = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        UserEntity actor = new UserEntity();
        StudentEntity entity = StudentEntity.builder().id(id).status("Effective").build();
        when(repo.findById(id)).thenReturn(Optional.of(entity));
        when(userRepo.getReferenceById(director)).thenReturn(actor);

        adapter.updateStatus(id, new StudentStatusChange(
            "Withdrawn", "Otro", "Se mudó a Santa Cruz con su familia.", director));

        assertThat(entity.getStatusNote()).isEqualTo("Se mudó a Santa Cruz con su familia.");
        assertThat(entity.getStatusChangedBy()).isSameAs(actor);
    }

    /**
     * When the change happened is stamped here rather than taken from the caller: a clock the
     * application passes in is a clock a caller can be wrong about.
     */
    @Test
    void updateStatus_stampsWhenItHappened() {
        UUID id = UUID.randomUUID();
        StudentEntity entity = StudentEntity.builder().id(id).status("Effective").build();
        when(repo.findById(id)).thenReturn(Optional.of(entity));

        adapter.updateStatus(id, new StudentStatusChange("Withdrawn", "Transferencia", null, null));

        assertThat(entity.getStatusChangedAt()).isNotNull();
    }

    /** A change with no author is recorded anyway. Asking for user null would be a 500. */
    @Test
    void updateStatus_withoutAnAuthor_looksUpNobody() {
        UUID id = UUID.randomUUID();
        StudentEntity entity = StudentEntity.builder().id(id).status("Effective").build();
        when(repo.findById(id)).thenReturn(Optional.of(entity));

        adapter.updateStatus(id, new StudentStatusChange("Withdrawn", "Otro", "x", null));

        assertThat(entity.getStatusChangedBy()).isNull();
        verify(userRepo, never()).getReferenceById(any(UUID.class));
    }

    @Test
    void updateStatus_missingStudent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.updateStatus(id, new StudentStatusChange(
            "Withdrawn", "Otro", "x", null)))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
