package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EnrollmentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaEnrollmentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class EnrollmentRepositoryAdapter implements IEnrollmentDomain {

    private final JpaEnrollmentRepository enrollmentRepo;
    private final JpaStudentRepository studentRepo;
    private final JpaClassGroupRepository classGroupRepo;

    public EnrollmentRepositoryAdapter(JpaEnrollmentRepository enrollmentRepo,
                                       JpaStudentRepository studentRepo,
                                       JpaClassGroupRepository classGroupRepo) {
        this.enrollmentRepo = enrollmentRepo;
        this.studentRepo = studentRepo;
        this.classGroupRepo = classGroupRepo;
    }

    @Override
    public boolean existsEnrollment(UUID studentId, UUID classGroupId) {
        return enrollmentRepo.existsByStudent_IdAndClassGroup_Id(studentId, classGroupId);
    }

    @Override
    @Transactional
    public void saveEnrollment(UUID studentId, UUID classGroupId) {
        StudentEntity student = studentRepo.getReferenceById(studentId);
        ClassGroupEntity classGroup = classGroupRepo.getReferenceById(classGroupId);
        EnrollmentEntity e = new EnrollmentEntity();
        e.setStudent(student);
        e.setClassGroup(classGroup);
        e.setEnrollmentDate(LocalDate.now());
        enrollmentRepo.save(e);
    }
}
