package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import java.util.List;
import java.util.Collection;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.StudentMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class StudentRepositoryAdapter implements IStudentDomain {

    private final JpaStudentRepository repo;
    private final JpaUserRepository userRepo;
    private final StudentMapper mapper;

    public StudentRepositoryAdapter(JpaStudentRepository repo, JpaUserRepository userRepo,
                                    StudentMapper mapper) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Student save(Student student) {
        StudentEntity entity = mapper.toEntity(student);
        return mapper.toDomain(repo.save(entity));
    }

    @Override
    public Optional<Student> findById(UUID id) {
        return repo.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Student> findByRudeCodeIn(Collection<String> rudeCodes) {
        if (rudeCodes == null || rudeCodes.isEmpty()) {
            return List.of();
        }
        return repo.findByRudeCodeIn(rudeCodes).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Student> findByIdentityCardIn(Collection<String> identityCards) {
        if (identityCards == null || identityCards.isEmpty()) {
            return List.of();
        }
        return repo.findByIdentityCardIn(identityCards).stream().map(mapper::toDomain).toList();
    }

    /**
     * Two queries and not one: a blank {@code q} takes the listing, so a null never reaches a
     * {@code LIKE}. Everything else about them is the same set of optional filters.
     */
    @Override
    public PageResult<StudentDirectoryItem> searchDirectory(StudentDirectoryQuery query,
                                                            PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        // Null narrows to nothing, which is what ALL means. The scope, not the caller, decides it.
        String status = query.scope().status();
        String q = query.q();
        if (q == null || q.isBlank()) {
            return SpringPaging.toPageResult(repo.listDirectory(
                query.courseId(), query.gradeId(), query.parallelId(), status, pageable));
        }
        return SpringPaging.toPageResult(repo.searchDirectory(
            q.trim(), query.courseId(), query.gradeId(), query.parallelId(), status, pageable));
    }

    @Override
    @Transactional
    public void updateStatus(UUID studentId, StudentStatusChange change) {
        StudentEntity entity = repo.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Estudiante", studentId));
        entity.setStatus(change.status());
        entity.setStatusReason(change.reason());
        entity.setStatusNote(change.note());
        // Stamped here rather than carried in: a clock the caller passes is a clock the caller can
        // be wrong about, and this is the row that says when a child left the school.
        entity.setStatusChangedAt(LocalDateTime.now());
        entity.setStatusChangedBy(change.changedBy() == null
            ? null
            : userRepo.getReferenceById(change.changedBy()));
        repo.save(entity);
    }
}
