package bo.edu.univalle.sis.ue6dejunio_api.application.services.courseenrollment;

import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.Set;
import java.util.Objects;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CourseEnrollmentService implements ICourseEnrollmentService {

    private static final String STATUS_EFFECTIVE = "Effective";
    private static final String STATUS_WITHDRAWN = "Withdrawn";

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
        // A roster is imported whole, so the three lookups it needs are done once for the whole
        // batch. Asking per student turned a class of forty into a hundred-odd round trips.
        Map<String, Student> byRude = indexBy(
            studentDomain.findByRudeCodeIn(valuesOf(command.students(), CreateStudentCommand::rudeCode)),
            Student::getRudeCode);
        Map<String, Student> byIdentityCard = indexBy(
            studentDomain.findByIdentityCardIn(valuesOf(command.students(), CreateStudentCommand::identityCard)),
            Student::getIdentityCard);
        // Every enrolment these students hold in the course, whatever its status, in one query. The
        // loop reads its two decisions off this map — seat still held, or closed row to reopen —
        // rather than asking the database once per name.
        Map<UUID, String> enrollmentInCourse = enrollmentDomain.enrollmentStatusByStudent(
            command.courseId(),
            Stream.concat(byRude.values().stream(), byIdentityCard.values().stream())
                .map(Student::getId).collect(Collectors.toSet()));
        Set<UUID> seatedInCourse = enrollmentInCourse.entrySet().stream()
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
        // Who the school is taking back. A set, so a file that repeats a name counts them once,
        // and written after the loop the same way the reopened enrolments are: listing someone on
        // a roster is the school saying they attend, and it used to be the one way of saying it
        // the record ignored — the import gave them a live enrolment while students.status stayed
        // 'Withdrawn', so the teacher saw them in the course and the directory did not.
        Set<UUID> readmitted = new HashSet<>();

        for (CreateStudentCommand sc : command.students()) {
            Student student = resolve(sc, byRude, byIdentityCard);
            if (student == null) {
                student = studentDomain.save(buildStudent(sc));
                created++;
                // Index what was just created: a PDF that repeats a row must not create it twice.
                index(byRude, student.getRudeCode(), student);
                index(byIdentityCard, student.getIdentityCard(), student);
            } else {
                existing++;
                // Only noted here. Who comes back is written once, after the roster has been read.
                if (STATUS_WITHDRAWN.equals(student.getStatus())) {
                    readmitted.add(student.getId());
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
        studentDomain.updateStatusIn(readmitted,
            new StudentStatusChange(STATUS_EFFECTIVE, null, null, command.actorId()));
        return new EnrollResult(
            command.students().size(), created, existing, readmitted.size(), enrolled, skipped);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseStudent> studentsOfCourse(UUID courseId, PageQuery pageQuery) {
        return enrollmentDomain.studentsByCourse(courseId, pageQuery);
    }

    /** The RUDE code identifies the student; the identity card is the fallback the ministry allows. */
    private static Student resolve(CreateStudentCommand sc, Map<String, Student> byRude,
                                   Map<String, Student> byIdentityCard) {
        Student student = sc.rudeCode() == null ? null : byRude.get(sc.rudeCode());
        if (student != null) {
            return student;
        }
        return sc.identityCard() == null ? null : byIdentityCard.get(sc.identityCard());
    }

    private static Set<String> valuesOf(List<CreateStudentCommand> students,
                                        Function<CreateStudentCommand, String> field) {
        return students.stream().map(field).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static Map<String, Student> indexBy(List<Student> students,
                                                Function<Student, String> key) {
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
