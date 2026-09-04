package bo.edu.univalle.sis.ue6dejunio_api.application.services.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawn;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.WithdrawStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
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
    private final IDomainEventPublisher events;
    private final IAcademicYearDomain academicYearDomain;

    public StudentService(IStudentDomain studentDomain,
                          ICourseEnrollmentDomain courseEnrollmentDomain,
                          IDomainEventPublisher events,
                          IAcademicYearDomain academicYearDomain) {
        this.studentDomain = studentDomain;
        this.courseEnrollmentDomain = courseEnrollmentDomain;
        this.events = events;
        this.academicYearDomain = academicYearDomain;
    }

    @Override
    @Transactional(readOnly = true)
    public Student getById(UUID id) {
        return studentDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Estudiante", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StudentDirectoryItem> search(StudentDirectoryQuery query,
                                                   PageQuery pageQuery) {
        return studentDomain.searchDirectory(inSomeGestion(query), pageQuery);
    }

    /**
     * A directory listing is always about one gestión, and this is where the caller who named none
     * gets given the current one.
     *
     * <p>Left open, the listing spans every year at once: a student who moved up holds an enrolment
     * per year and comes back once for each, while the total counts the person once. Pinning the
     * year is what makes the rows and the total agree.
     *
     * <p>A course is left alone, because a course already belongs to exactly one gestión. Narrowing
     * to the current year on top of it would answer with nothing for every teacher whose course is
     * not this year's.
     */
    private StudentDirectoryQuery inSomeGestion(StudentDirectoryQuery query) {
        if (query.academicYearId() != null || query.courseId() != null) {
            return query;
        }
        return query.inAcademicYear(academicYearDomain.currentYearId());
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

        // Only the fact leaves here. Which teachers have to hear about it is a question about the
        // school, not about a student record, and it is answered on the notification side.
        events.publish(new StudentWithdrawn(
            command.studentId(), student.fullName(), reason.label(), note));
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
