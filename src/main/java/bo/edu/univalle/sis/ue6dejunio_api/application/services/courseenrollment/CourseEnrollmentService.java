package bo.edu.univalle.sis.ue6dejunio_api.application.services.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class CourseEnrollmentService implements ICourseEnrollmentService {

    private static final String STATUS_EFFECTIVE = "Effective";

    private final IStudentDomain studentDomain;
    private final ICourseEnrollmentDomain enrollmentDomain;

    public CourseEnrollmentService(IStudentDomain studentDomain, ICourseEnrollmentDomain enrollmentDomain) {
        this.studentDomain = studentDomain;
        this.enrollmentDomain = enrollmentDomain;
    }

    @Override
    @Transactional
    public EnrollResult enroll(EnrollToCourseCommand command) {
        if (!enrollmentDomain.courseExists(command.courseId())) {
            throw new ResourceNotFoundException("Course", command.courseId());
        }
        int created = 0;
        int existing = 0;
        int enrolled = 0;
        int skipped = 0;

        for (CreateStudentCommand sc : command.students()) {
            Student student = findExisting(sc);
            if (student == null) {
                student = studentDomain.save(buildStudent(sc));
                created++;
            } else {
                existing++;
            }
            if (enrollmentDomain.existsEnrollment(student.getId(), command.courseId())) {
                skipped++;
            } else {
                enrollmentDomain.saveEnrollment(student.getId(), command.courseId());
                enrolled++;
            }
        }
        return new EnrollResult(command.students().size(), created, existing, enrolled, skipped);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseStudent> studentsOfCourse(UUID courseId, Pageable pageable) {
        return enrollmentDomain.studentsByCourse(courseId, pageable);
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
