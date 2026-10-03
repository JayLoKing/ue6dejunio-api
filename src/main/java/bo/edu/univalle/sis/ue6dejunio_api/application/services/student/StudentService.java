package bo.edu.univalle.sis.ue6dejunio_api.application.services.student;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollmentSeat;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.NotReenrolledReport;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.ReinstateResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.ReinstateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentMovementSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawn;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.WithdrawStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService implements IStudentService {

    private static final String STATUS_WITHDRAWN = "Withdrawn";
    private static final String STATUS_EFFECTIVE = "Effective";

    private final IStudentDomain studentDomain;
    private final ICourseEnrollmentDomain courseEnrollmentDomain;
    private final IDomainEventPublisher events;
    private final IAcademicYearDomain academicYearDomain;
    private final ICourseDomain courseDomain;
    private final ClosedYearGuard closedYearGuard;

    public StudentService(
            IStudentDomain studentDomain,
            ICourseEnrollmentDomain courseEnrollmentDomain,
            IDomainEventPublisher events,
            IAcademicYearDomain academicYearDomain,
            ICourseDomain courseDomain,
            ClosedYearGuard closedYearGuard) {
        this.studentDomain = studentDomain;
        this.courseEnrollmentDomain = courseEnrollmentDomain;
        this.events = events;
        this.academicYearDomain = academicYearDomain;
        this.courseDomain = courseDomain;
        this.closedYearGuard = closedYearGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public Student getById(UUID id) {
        return studentDomain
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estudiante", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StudentDirectoryItem> search(
            StudentDirectoryQuery query, PageQuery pageQuery) {
        return studentDomain.searchDirectory(inSomeGestion(query), pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentMovementSummary movementSummary(Integer academicYearId) {
        // The same rule the directory follows: a caller who named no gestión gets the current one,
        // rather than a table that spans every year and counts a student who moved up once per
        // year they were enrolled in.
        Integer year = academicYearId == null ? academicYearDomain.currentYearId() : academicYearId;
        return studentDomain.movementSummary(year);
    }

    @Override
    @Transactional(readOnly = true)
    public NotReenrolledReport notReenrolled(Integer academicYearId) {
        Integer yearId =
                academicYearId == null ? academicYearDomain.currentYearId() : academicYearId;
        NotReenrolledReport.YearRef reference = yearRef(yearId);
        Integer previousId = academicYearDomain.previousYearIdOf(yearId).orElse(null);
        if (previousId == null) {
            return new NotReenrolledReport(reference, null, List.of());
        }
        return new NotReenrolledReport(
                reference, yearRef(previousId), studentDomain.notReenrolled(yearId, previousId));
    }

    private NotReenrolledReport.YearRef yearRef(Integer yearId) {
        return academicYearDomain
                .findItem(yearId)
                .map(y -> new NotReenrolledReport.YearRef(y.id(), y.year()))
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearId));
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
            throw new ValidationException("Una baja por otro motivo exige decir cual es");
        }

        Student student =
                studentDomain
                        .findById(command.studentId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Estudiante", command.studentId()));
        if (STATUS_WITHDRAWN.equals(student.getStatus())) {
            throw new ConflictException("El estudiante ya se encuentra dado de baja");
        }

        studentDomain.updateStatus(
                command.studentId(),
                new StudentStatusChange(STATUS_WITHDRAWN, reason.label(), note, command.actorId()));
        courseEnrollmentDomain.withdrawActiveEnrollments(command.studentId());

        // Only the fact leaves here. Which teachers have to hear about it is a question about the
        // school, not about a student record, and it is answered on the notification side.
        events.publish(
                new StudentWithdrawn(
                        command.studentId(), student.fullName(), reason.label(), note));
    }

    @Override
    @Transactional
    public ReinstateResult reinstate(ReinstateStudentCommand command) {
        Student student =
                studentDomain
                        .findById(command.studentId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Estudiante", command.studentId()));
        if (!STATUS_WITHDRAWN.equals(student.getStatus())) {
            throw new ConflictException("El estudiante no se encuentra dado de baja");
        }

        // A reinstatement is always into the gestión that is running: a seat in a past year is
        // history, and a closed one refuses every write.
        Integer yearId = academicYearDomain.currentYearId();
        closedYearGuard.requireOpenForYear(yearId);

        List<EnrollmentSeat> withdrawnSeats =
                courseEnrollmentDomain.seatsOfStudentInYear(command.studentId(), yearId).stream()
                        .filter(seat -> STATUS_WITHDRAWN.equals(seat.status()))
                        .toList();
        UUID courseId;
        if (withdrawnSeats.isEmpty()) {
            courseId = requireCourseOfYear(command.courseId(), yearId);
        } else {
            courseId = seatToReopen(withdrawnSeats, command.courseId());
        }

        // UNIQUE (id_student, id_course): a row that already exists is reopened, never doubled.
        boolean reactivated =
                !withdrawnSeats.isEmpty()
                        || courseEnrollmentDomain
                                .enrollmentStatusByStudent(courseId, Set.of(command.studentId()))
                                .containsKey(command.studentId());
        if (reactivated) {
            courseEnrollmentDomain.reactivateEnrollments(courseId, Set.of(command.studentId()));
        } else {
            courseEnrollmentDomain.saveEnrollment(command.studentId(), courseId);
        }

        studentDomain.updateStatus(
                command.studentId(),
                new StudentStatusChange(STATUS_EFFECTIVE, null, null, command.actorId()));
        return new ReinstateResult(command.studentId(), STATUS_EFFECTIVE, courseId, reactivated);
    }

    /**
     * The seat the student returns to. Naming a different course is refused rather than followed:
     * the student already holds a seat this gestión, and a second one is not what a reinstatement
     * is for.
     */
    private UUID seatToReopen(List<EnrollmentSeat> withdrawnSeats, UUID requestedCourseId) {
        if (requestedCourseId == null) {
            return withdrawnSeats.getFirst().courseId();
        }
        if (withdrawnSeats.stream().noneMatch(seat -> requestedCourseId.equals(seat.courseId()))) {
            throw new ValidationException(
                    "El estudiante ya tiene un lugar esta gestion en el curso "
                            + courseLabel(withdrawnSeats.getFirst().courseId()));
        }
        return requestedCourseId;
    }

    private UUID requireCourseOfYear(UUID courseId, Integer yearId) {
        if (courseId == null) {
            throw new ValidationException(
                    "Indica el curso de la gestion actual donde se reincorpora al estudiante");
        }
        Course course =
                courseDomain
                        .findById(courseId)
                        .orElseThrow(() -> new ResourceNotFoundException("Curso", courseId));
        if (!yearId.equals(course.academicYearId())) {
            throw new ValidationException("El curso no pertenece a la gestion actual");
        }
        return courseId;
    }

    private String courseLabel(UUID courseId) {
        return courseDomain
                .findById(courseId)
                .map(c -> c.gradeName() + " " + c.parallelName())
                .orElse(courseId.toString());
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
