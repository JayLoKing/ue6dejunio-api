package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.TeacherStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EnrollmentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaEnrollmentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Override
    public Optional<UUID> findEnrollmentId(UUID studentId, UUID classGroupId) {
        return enrollmentRepo.findByStudent_IdAndClassGroup_Id(studentId, classGroupId)
            .map(EnrollmentEntity::getId);
    }

    @Override
    public Page<TeacherStudent> studentsByTeacher(UUID teacherId, Integer yearId, Pageable pageable) {
        Page<StudentEntity> studentsPage = enrollmentRepo
            .findDistinctStudentsByTeacherPaged(teacherId, yearId, pageable);
        List<UUID> ids = studentsPage.getContent().stream().map(StudentEntity::getId).toList();
        Map<UUID, List<EnrollmentEntity>> byStudent = ids.isEmpty()
            ? Map.of()
            : enrollmentRepo.findEnrollmentsByStudentsAndTeacher(ids, teacherId, yearId).stream()
                .collect(Collectors.groupingBy(e -> e.getStudent().getId()));
        return studentsPage.map(s -> toTeacherStudent(s, byStudent.getOrDefault(s.getId(), List.of())));
    }

    private TeacherStudent toTeacherStudent(StudentEntity s, List<EnrollmentEntity> enrolls) {
        List<TeacherStudent.SubjectEnrollment> subjects = enrolls.stream()
            .map(e -> new TeacherStudent.SubjectEnrollment(
                e.getId(),
                e.getClassGroup().getId(),
                e.getClassGroup().getSubject().getId(),
                e.getClassGroup().getSubject().getName()))
            .toList();
        return new TeacherStudent(
            s.getId(), s.getRudeCode(), s.getIdentityCard(), s.getNames(), s.getLastNames(),
            s.getBirthDate(), s.getGender(), s.getStatus(), subjects
        );
    }
}
