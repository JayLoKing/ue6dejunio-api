package bo.edu.univalle.sis.ue6dejunio_api.application.services.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StudentService implements IStudentService {

    private static final String STATUS_WITHDRAWN = "Withdrawn";

    private final IStudentDomain studentDomain;
    private final ICourseEnrollmentDomain courseEnrollmentDomain;

    public StudentService(IStudentDomain studentDomain, ICourseEnrollmentDomain courseEnrollmentDomain) {
        this.studentDomain = studentDomain;
        this.courseEnrollmentDomain = courseEnrollmentDomain;
    }

    @Override
    @Transactional(readOnly = true)
    public Student getById(UUID id) {
        return studentDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Estudiante", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StudentDirectoryItem> search(String q, UUID courseId, Pageable pageable) {
        return studentDomain.searchDirectory(q, courseId, pageable);
    }

    @Override
    @Transactional
    public void withdraw(UUID studentId, StudentWithdrawalReason reason) {
        if (reason == null) {
            throw new ValidationException("Motivo de baja invalido");
        }
        Student student = studentDomain.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Estudiante", studentId));
        if (STATUS_WITHDRAWN.equals(student.getStatus())) {
            throw new ConflictException("El estudiante ya se encuentra dado de baja");
        }
        studentDomain.updateStatus(studentId, STATUS_WITHDRAWN, reason.label());
        courseEnrollmentDomain.withdrawActiveEnrollments(studentId);
    }
}
