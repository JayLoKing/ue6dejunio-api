package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.StudentMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class StudentRepositoryAdapter implements IStudentDomain {

    private final JpaStudentRepository repo;
    private final StudentMapper mapper;

    public StudentRepositoryAdapter(JpaStudentRepository repo, StudentMapper mapper) {
        this.repo = repo;
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
    public java.util.Optional<Student> findByRudeCode(String rudeCode) {
        return repo.findByRudeCode(rudeCode).map(mapper::toDomain);
    }

    @Override
    public java.util.Optional<Student> findByIdentityCard(String identityCard) {
        return repo.findByIdentityCard(identityCard).map(mapper::toDomain);
    }

    @Override
    public boolean existsByRudeCode(String rudeCode) {
        return repo.existsByRudeCode(rudeCode);
    }

    @Override
    public boolean existsByIdentityCard(String identityCard) {
        return repo.existsByIdentityCard(identityCard);
    }
}
