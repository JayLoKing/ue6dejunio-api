package bo.edu.univalle.sis.ue6dejunio_api.application.services.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.TeacherStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EnrollmentService implements IEnrollmentService {

    private static final String STATUS_EFFECTIVE = "Effective";

    private final IStudentDomain studentDomain;
    private final IClassGroupDomain classGroupDomain;
    private final IEnrollmentDomain enrollmentDomain;

    public EnrollmentService(IStudentDomain studentDomain,
                             IClassGroupDomain classGroupDomain,
                             IEnrollmentDomain enrollmentDomain) {
        this.studentDomain = studentDomain;
        this.classGroupDomain = classGroupDomain;
        this.enrollmentDomain = enrollmentDomain;
    }

    @Override
    @Transactional
    public EnrollResult enrollCourse(EnrollCourseCommand command) {
        Integer yearId = classGroupDomain.currentAcademicYearId();
        List<UUID> classGroupIds = classGroupDomain.classGroupIdsByCourse(
            command.gradeId(), command.parallelId(), yearId);

        if (classGroupIds.isEmpty()) {
            throw new ResourceNotFoundException("Curso sin materias asignadas (class_groups)",
                "grado=" + command.gradeId() + " paralelo=" + command.parallelId());
        }

        int studentsCreated = 0;
        int studentsExisting = 0;
        int enrollmentsCreated = 0;
        int enrollmentsSkipped = 0;

        for (CreateStudentCommand sc : command.students()) {
            Student student = findExisting(sc);
            if (student == null) {
                student = studentDomain.save(buildStudent(sc));
                studentsCreated++;
            } else {
                studentsExisting++;
            }
            for (UUID cgId : classGroupIds) {
                if (enrollmentDomain.existsEnrollment(student.getId(), cgId)) {
                    enrollmentsSkipped++;
                } else {
                    enrollmentDomain.saveEnrollment(student.getId(), cgId);
                    enrollmentsCreated++;
                }
            }
        }

        return new EnrollResult(
            command.students().size(), studentsCreated, studentsExisting,
            classGroupIds.size(), enrollmentsCreated, enrollmentsSkipped
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TeacherStudent> studentsOfTeacher(UUID teacherId, Pageable pageable) {
        Integer yearId = classGroupDomain.currentAcademicYearId();
        return enrollmentDomain.studentsByTeacher(teacherId, yearId, pageable);
    }

    private Student findExisting(CreateStudentCommand sc) {
        Optional<Student> byRude = studentDomain.findByRudeCode(sc.rudeCode());
        if (byRude.isPresent()) {
            return byRude.get();
        }
        return studentDomain.findByIdentityCard(sc.identityCard()).orElse(null);
    }

    private Student buildStudent(CreateStudentCommand sc) {
        return Student.builder()
            .rudeCode(sc.rudeCode())
            .identityCard(sc.identityCard())
            .names(sc.names())
            .lastNames(sc.lastNames())
            .birthDate(sc.birthDate())
            .gender(sc.gender())
            .status(STATUS_EFFECTIVE)
            .build();
    }
}
