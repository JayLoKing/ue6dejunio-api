package bo.edu.univalle.sis.ue6dejunio_api.application.services.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollConflict;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrolledElsewhere;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.SkippedStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.YearSeat;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseEnrollmentService implements ICourseEnrollmentService {

    private static final String STATUS_EFFECTIVE = "Effective";
    private static final String STATUS_WITHDRAWN = "Withdrawn";

    private final IStudentDomain studentDomain;
    private final ICourseEnrollmentDomain enrollmentDomain;
    private final IAcademicYearDomain academicYearDomain;
    private final ClosedYearGuard closedYearGuard;

    public CourseEnrollmentService(
            IStudentDomain studentDomain,
            ICourseEnrollmentDomain enrollmentDomain,
            IAcademicYearDomain academicYearDomain,
            ClosedYearGuard closedYearGuard) {
        this.studentDomain = studentDomain;
        this.enrollmentDomain = enrollmentDomain;
        this.academicYearDomain = academicYearDomain;
        this.closedYearGuard = closedYearGuard;
    }

    @Override
    @Transactional
    public EnrollResult enroll(EnrollToCourseCommand command) {
        if (!enrollmentDomain.courseExists(command.courseId())) {
            throw new ResourceNotFoundException("Course", command.courseId());
        }
        // Before the first lookup, and so before any student is created: a roster import is the one
        // enrolment write that also writes students, and none of it may land on a closed year.
        closedYearGuard.requireOpenForCourse(command.courseId());
        // A roster is imported whole, so the three lookups it needs are done once for the whole
        // batch. Asking per student turned a class of forty into a hundred-odd round trips.
        Map<String, Student> byRude =
                indexBy(
                        studentDomain.findByRudeCodeIn(
                                valuesOf(command.students(), CreateStudentCommand::rudeCode)),
                        Student::getRudeCode);
        Map<String, Student> byIdentityCard =
                indexBy(
                        studentDomain.findByIdentityCardIn(
                                valuesOf(command.students(), CreateStudentCommand::identityCard)),
                        Student::getIdentityCard);
        // Every enrolment these students hold in the course, whatever its status, in one query. The
        // loop reads its two decisions off this map — seat still held, or closed row to reopen —
        // rather than asking the database once per name.
        Set<UUID> known =
                Stream.concat(byRude.values().stream(), byIdentityCard.values().stream())
                        .map(Student::getId)
                        .collect(Collectors.toSet());
        Map<UUID, String> enrollmentInCourse =
                enrollmentDomain.enrollmentStatusByStudent(command.courseId(), known);
        // And every seat they hold anywhere in the course's gestión, in one more query: the loop
        // reads off it who already sits in a sibling course of the same year.
        Map<UUID, List<YearSeat>> seatsInYear =
                enrollmentDomain.seatsInYearOfCourse(command.courseId(), known).stream()
                        .collect(Collectors.groupingBy(YearSeat::studentId));
        Set<UUID> seatedInCourse =
                enrollmentInCourse.entrySet().stream()
                        .filter(e -> STATUS_EFFECTIVE.equals(e.getValue()))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toCollection(HashSet::new));
        // Reopened together once the roster has been read: one statement for the whole import
        // instead of one per student who came back.
        Set<UUID> toReactivate = new HashSet<>();

        int created = 0;
        int existing = 0;
        int enrolled = 0;
        int skipped = 0;
        // Withdrawn students the roster named. Only the Director's reinstatement puts one back, so
        // the load leaves them alone and reports them. Keyed by id so a file that repeats a name
        // lists them once; the name comes from the row already loaded, not from another query.
        Map<UUID, SkippedStudent> skippedWithdrawn = new LinkedHashMap<>();
        // Students already seated in another course of this gestión. A second seat in the same year
        // is what the Director's reinstatement or transfer decides, never a roster load.
        Map<UUID, EnrolledElsewhere> skippedOtherCourse = new LinkedHashMap<>();
        // Existing students, not withdrawn, that this load did not create: the ones whose past the
        // summary has something to say about. A set, so a repeated row counts once.
        Set<UUID> carriedOver = new HashSet<>();
        Set<UUID> createdNow = new HashSet<>();
        // Rows whose RUDE and CI belong to two different students. They name nobody for certain,
        // so they are left out whole; a set, so a repeated row is reported once.
        Set<EnrollConflict> conflicts = new LinkedHashSet<>();

        for (CreateStudentCommand sc : command.students()) {
            Optional<EnrollConflict> conflict = conflictOf(sc, byRude, byIdentityCard);
            if (conflict.isPresent()) {
                conflicts.add(conflict.get());
                continue;
            }
            Student student = resolve(sc, byRude, byIdentityCard);
            if (student == null) {
                student = studentDomain.save(buildStudent(sc));
                createdNow.add(student.getId());
                created++;
                // Index what was just created: a PDF that repeats a row must not create it twice.
                index(byRude, student.getRudeCode(), student);
                index(byIdentityCard, student.getIdentityCard(), student);
            } else {
                existing++;
                if (STATUS_WITHDRAWN.equals(student.getStatus())) {
                    skippedWithdrawn.putIfAbsent(
                            student.getId(),
                            new SkippedStudent(student.getId(), student.fullName()));
                    continue;
                }
                if (!createdNow.contains(student.getId())) {
                    Optional<EnrolledElsewhere> elsewhere =
                            seatedElsewhere(
                                    student, command.courseId(), enrollmentInCourse, seatsInYear);
                    if (elsewhere.isPresent()) {
                        skippedOtherCourse.putIfAbsent(student.getId(), elsewhere.get());
                        continue;
                    }
                    carriedOver.add(student.getId());
                }
            }
            // add() is false when the id is already there, which covers both a seat still held from
            // an earlier import and the same student appearing twice in this one.
            if (seatedInCourse.add(student.getId())) {
                // A closed row is the only way back into a course they already left: UNIQUE
                // (id_student, id_course) leaves nothing to insert alongside it.
                if (enrollmentInCourse.containsKey(student.getId())) {
                    toReactivate.add(student.getId());
                } else {
                    enrollmentDomain.saveEnrollment(student.getId(), command.courseId());
                }
                enrolled++;
            } else {
                skipped++;
            }
        }
        enrollmentDomain.reactivateEnrollments(command.courseId(), toReactivate);
        int fromPreviousYear = fromPreviousYear(command.courseId(), carriedOver);
        return new EnrollResult(
                command.students().size(),
                created,
                existing,
                fromPreviousYear,
                carriedOver.size() - fromPreviousYear,
                enrolled,
                skipped,
                List.copyOf(skippedWithdrawn.values()),
                List.copyOf(skippedOtherCourse.values()),
                List.copyOf(conflicts));
    }

    /**
     * The row's RUDE code and identity card each found a student, and not the same one. Resolving
     * by RUDE alone would enrol that student on a row half of which describes somebody else.
     */
    private static Optional<EnrollConflict> conflictOf(
            CreateStudentCommand sc,
            Map<String, Student> byRude,
            Map<String, Student> byIdentityCard) {
        Student byCode = sc.rudeCode() == null ? null : byRude.get(sc.rudeCode());
        Student byCard = sc.identityCard() == null ? null : byIdentityCard.get(sc.identityCard());
        if (byCode == null || byCard == null || byCode.getId().equals(byCard.getId())) {
            return Optional.empty();
        }
        return Optional.of(
                new EnrollConflict(
                        sc.rudeCode(),
                        sc.identityCard(),
                        sc.names() + " " + sc.lastNames(),
                        "El RUDE corresponde a "
                                + byCode.fullName()
                                + " y el CI a "
                                + byCard.fullName()
                                + "."));
    }

    /**
     * The Effective seat the student already holds in another course of the gestión, named for the
     * summary, or empty when there is none.
     *
     * <p>A student already in force in the course being loaded is left to the ordinary repeat rule
     * instead: the admin reinstatement and transfer paths can legitimately leave someone with a
     * second seat in the year, and loading their own course again must not report them as lost.
     */
    private static Optional<EnrolledElsewhere> seatedElsewhere(
            Student student,
            UUID courseId,
            Map<UUID, String> enrollmentInCourse,
            Map<UUID, List<YearSeat>> seatsInYear) {
        if (STATUS_EFFECTIVE.equals(enrollmentInCourse.get(student.getId()))) {
            return Optional.empty();
        }
        return seatsInYear.getOrDefault(student.getId(), List.of()).stream()
                .filter(seat -> !courseId.equals(seat.courseId()))
                .filter(seat -> STATUS_EFFECTIVE.equals(seat.status()))
                .findFirst()
                .map(
                        seat ->
                                new EnrolledElsewhere(
                                        student.getId(),
                                        student.getRudeCode(),
                                        student.fullName(),
                                        seat.courseLabel()));
    }

    /**
     * How many of these students sat an Effective seat in the gestión before the course's own: one
     * query for the whole set, and none when there is nobody to ask about or nothing before.
     */
    private int fromPreviousYear(UUID courseId, Set<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return 0;
        }
        return academicYearDomain
                .previousYearIdOfCourse(courseId)
                .map(year -> enrollmentDomain.studentsEffectiveInYear(year, studentIds).size())
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseStudent> studentsOfCourse(UUID courseId, PageQuery pageQuery) {
        return enrollmentDomain.studentsByCourse(courseId, pageQuery);
    }

    /**
     * The RUDE code identifies the student; the identity card is the fallback the ministry allows.
     */
    private static Student resolve(
            CreateStudentCommand sc,
            Map<String, Student> byRude,
            Map<String, Student> byIdentityCard) {
        Student student = sc.rudeCode() == null ? null : byRude.get(sc.rudeCode());
        if (student != null) {
            return student;
        }
        return sc.identityCard() == null ? null : byIdentityCard.get(sc.identityCard());
    }

    private static Set<String> valuesOf(
            List<CreateStudentCommand> students, Function<CreateStudentCommand, String> field) {
        return students.stream().map(field).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static Map<String, Student> indexBy(
            List<Student> students, Function<Student, String> key) {
        Map<String, Student> index = new HashMap<>();
        for (Student s : students) {
            index(index, key.apply(s), s);
        }
        return index;
    }

    private static void index(Map<String, Student> index, String key, Student student) {
        if (key != null) {
            index.putIfAbsent(key, student);
        }
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
