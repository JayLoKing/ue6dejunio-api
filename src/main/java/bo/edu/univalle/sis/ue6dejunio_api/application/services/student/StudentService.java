package bo.edu.univalle.sis.ue6dejunio_api.application.services.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.WithdrawStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
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
    public PageResult<StudentDirectoryItem> search(String q, UUID courseId, PageQuery pageQuery) {
        return studentDomain.searchDirectory(q, courseId, pageQuery);
    }

    @Override
    @Transactional
    public void withdraw(WithdrawStudentCommand command) {
        StudentWithdrawalReason reason = command.reason();
        if (reason == null) {
            throw new ValidationException("Motivo de baja invalido");
        }
        String note = blankToNull(command.note());
        // The open category is the one that has to say what it means. Stored alone it puts the
        // word "Otro" in front of a teacher and answers nothing.
        if (reason.needsItsOwnWords() && note == null) {
            throw new ValidationException(
                "Una baja por otro motivo exige decir cual es");
        }

        Student student = studentDomain.findById(command.studentId())
            .orElseThrow(() -> new ResourceNotFoundException("Estudiante", command.studentId()));
        if (STATUS_WITHDRAWN.equals(student.getStatus())) {
            throw new ConflictException("El estudiante ya se encuentra dado de baja");
        }

        studentDomain.updateStatus(command.studentId(), new StudentStatusChange(
            STATUS_WITHDRAWN, reason.label(), note, command.actorId()));
        courseEnrollmentDomain.withdrawActiveEnrollments(command.studentId());
    }

    /** Whitespace is not a note. Kept as absent, so nobody is shown an empty line. */
    private static String blankToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
