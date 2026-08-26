package bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseOverview;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentTrimesterSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.SubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GradebookService implements IGradebookService {

    private final ICourseEnrollmentDomain enrollmentDomain;
    private final IScoreDomain scoreDomain;
    private final IAttendanceDomain attendanceDomain;
    private final ICourseService courseService;
    private final IClassGroupDomain classGroupDomain;

    public GradebookService(ICourseEnrollmentDomain enrollmentDomain,
                            IScoreDomain scoreDomain,
                            IAttendanceDomain attendanceDomain,
                            ICourseService courseService,
                            IClassGroupDomain classGroupDomain) {
        this.enrollmentDomain = enrollmentDomain;
        this.scoreDomain = scoreDomain;
        this.attendanceDomain = attendanceDomain;
        this.courseService = courseService;
        this.classGroupDomain = classGroupDomain;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentTrimesterSummary studentSummary(UUID courseEnrollmentId, Integer trimester) {
        CourseStudent cs = enrollmentDomain.courseStudentById(courseEnrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId));
        return buildSummary(courseEnrollmentId, cs.studentId(), cs.fullName(), trimester);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StudentTrimesterSummary> centralizer(UUID courseId, Integer trimester, PageQuery pageQuery) {
        PageResult<CourseStudent> page = enrollmentDomain.studentsByCourse(courseId, pageQuery);
        List<UUID> ids = page.content().stream().map(CourseStudent::courseEnrollmentId).toList();
        Map<UUID, List<AcademicScore>> grouped = ids.isEmpty()
            ? Map.of()
            : scoreDomain.findByCourseEnrollmentIn(ids).stream()
                .collect(Collectors.groupingBy(AcademicScore::courseEnrollmentId));
        return page.map(cs -> buildSummary(cs.courseEnrollmentId(), cs.studentId(), cs.fullName(), trimester,
            grouped.getOrDefault(cs.courseEnrollmentId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseAttendanceRow> courseAttendance(UUID courseId, LocalDate date, PageQuery pageQuery) {
        PageResult<CourseStudent> page = enrollmentDomain.activeStudentsByCourse(courseId, pageQuery);
        return attendanceRows(page, date, attendanceDomain::dailyByCourseEnrollmentIn);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseAttendanceRow> classGroupAttendance(UUID classGroupId, LocalDate date,
                                                          PageQuery pageQuery) {
        // The roster is the course's, but the rows are the subject's: a technical teacher marks the
        // same students the homeroom teacher does, only for their own class group.
        UUID courseId = attendanceDomain.courseOfClassGroup(classGroupId);
        PageResult<CourseStudent> page = enrollmentDomain.activeStudentsByCourse(courseId, pageQuery);
        return attendanceRows(page, date,
            ids -> attendanceDomain.sessionByClassGroupAndCourseEnrollmentIn(classGroupId, ids));
    }

    /** One batched load for the whole page, then in-memory grouping — never a query per student. */
    private PageResult<CourseAttendanceRow> attendanceRows(PageResult<CourseStudent> page, LocalDate date,
                                                     Function<List<UUID>, List<Attendance>> loader) {
        List<UUID> ids = page.content().stream().map(CourseStudent::courseEnrollmentId).toList();
        Map<UUID, List<Attendance>> grouped = ids.isEmpty()
            ? Map.of()
            : loader.apply(ids).stream()
                .collect(Collectors.groupingBy(Attendance::courseEnrollmentId));
        return page.map(cs -> {
            List<Attendance> att = grouped.getOrDefault(cs.courseEnrollmentId(), List.of());
            if (date != null) {
                att = att.stream().filter(a -> date.equals(a.date())).toList();
            }
            return new CourseAttendanceRow(cs.courseEnrollmentId(), cs.studentId(), cs.fullName(), att);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CourseOverview courseOverview(UUID courseId, Integer trimester, PageQuery pageQuery) {
        Course course = courseService.getById(courseId);
        List<ClassGroup> classGroups = classGroupDomain.byCourse(courseId);
        PageResult<StudentTrimesterSummary> students = centralizer(courseId, trimester, pageQuery);
        return new CourseOverview(course, classGroups, students);
    }

    private StudentTrimesterSummary buildSummary(UUID courseEnrollmentId, UUID studentId,
                                                 String fullName, Integer trimester) {
        return buildSummary(courseEnrollmentId, studentId, fullName, trimester,
            scoreDomain.findByCourseEnrollment(courseEnrollmentId));
    }

    /** Overload consuming a pre-grouped (batch-loaded) score list; keeps the averaging math verbatim. */
    private StudentTrimesterSummary buildSummary(UUID courseEnrollmentId, UUID studentId,
                                                 String fullName, Integer trimester,
                                                 List<AcademicScore> allScores) {
        List<AcademicScore> scores = allScores.stream()
            .filter(s -> trimester.equals(s.trimester()))
            .toList();
        List<SubjectScore> subjects = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (AcademicScore s : scores) {
            BigDecimal total = s.totalScore();
            boolean graded = total != null;
            sum = sum.add(graded ? total : BigDecimal.ZERO);
            subjects.add(new SubjectScore(s.classGroupId(), s.subjectName(), total, graded));
        }
        BigDecimal general = subjects.isEmpty()
            ? null
            : sum.divide(BigDecimal.valueOf(subjects.size()), 2, RoundingMode.HALF_UP);
        return new StudentTrimesterSummary(courseEnrollmentId, studentId, fullName, trimester, subjects, general);
    }
}
