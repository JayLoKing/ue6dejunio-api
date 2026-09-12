package bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualSubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseOverview;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
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
        Map<UUID, List<AcademicScore>> grouped = scoresByEnrollment(page);
        return page.map(cs -> buildSummary(cs.courseEnrollmentId(), cs.studentId(), cs.fullName(), trimester,
            grouped.getOrDefault(cs.courseEnrollmentId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StudentAnnualSummary> annualCentralizer(UUID courseId, PageQuery pageQuery) {
        // No gestión parameter: the course already belongs to one academic year, so asking for the
        // year again would only let a caller contradict the course it just named. The batched load
        // is the same one the trimester centralizer does — it never filtered by trimester in SQL —
        // so reading the whole year costs the same single query.
        PageResult<CourseStudent> page = enrollmentDomain.studentsByCourse(courseId, pageQuery);
        Map<UUID, List<AcademicScore>> grouped = scoresByEnrollment(page);
        return page.map(cs -> buildAnnualSummary(cs.courseEnrollmentId(), cs.studentId(), cs.fullName(),
            grouped.getOrDefault(cs.courseEnrollmentId(), List.of())));
    }

    /** One batched load for the whole page, then in-memory grouping — never a query per student. */
    private Map<UUID, List<AcademicScore>> scoresByEnrollment(PageResult<CourseStudent> page) {
        List<UUID> ids = page.content().stream().map(CourseStudent::courseEnrollmentId).toList();
        return ids.isEmpty()
            ? Map.of()
            : scoreDomain.findByCourseEnrollmentIn(ids).stream()
                .collect(Collectors.groupingBy(AcademicScore::courseEnrollmentId));
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
        List<AcademicScore> scores = ofTrimester(allScores, trimester);
        List<SubjectScore> subjects = scores.stream()
            .map(s -> new SubjectScore(s.classGroupId(), s.subjectName(), s.totalScore(),
                s.totalScore() != null))
            .toList();
        return new StudentTrimesterSummary(courseEnrollmentId, studentId, fullName, trimester,
            subjects, averageOfTotals(scores));
    }

    private StudentAnnualSummary buildAnnualSummary(UUID courseEnrollmentId, UUID studentId,
                                                    String fullName, List<AcademicScore> allScores) {
        List<AnnualSubjectScore> subjects = allScores.stream()
            .collect(Collectors.groupingBy(AcademicScore::classGroupId, LinkedHashMap::new,
                Collectors.toList()))
            .values().stream()
            .map(GradebookService::annualSubject)
            // A report whose columns move between two loads is unreadable, and the order a batched
            // query answers in is not guaranteed. The class group breaks ties so two areas sharing
            // a name still land somewhere fixed.
            .sorted(Comparator
                .comparing(AnnualSubjectScore::subjectName,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.classGroupId().toString()))
            .toList();
        // No null filter here: a group exists because it had at least one row, so
        // averageOfTotals never answered null for it. Guarding against it would be dead code
        // claiming a case the grouping cannot produce.
        List<BigDecimal> areaAverages = subjects.stream()
            .map(AnnualSubjectScore::average)
            .toList();
        return new StudentAnnualSummary(courseEnrollmentId, studentId, fullName, subjects,
            averageOfTotals(ofTrimester(allScores, 1)),
            averageOfTotals(ofTrimester(allScores, 2)),
            averageOfTotals(ofTrimester(allScores, 3)),
            mean(areaAverages));
    }

    /** One area's year: its total per trimester, and the mean of the trimesters it was graded in. */
    private static AnnualSubjectScore annualSubject(List<AcademicScore> rowsOfOneClassGroup) {
        AcademicScore any = rowsOfOneClassGroup.get(0);
        return new AnnualSubjectScore(any.classGroupId(), any.subjectName(),
            totalOfTrimester(rowsOfOneClassGroup, 1),
            totalOfTrimester(rowsOfOneClassGroup, 2),
            totalOfTrimester(rowsOfOneClassGroup, 3),
            averageOfTotals(rowsOfOneClassGroup));
    }

    private static List<AcademicScore> ofTrimester(List<AcademicScore> scores, Integer trimester) {
        return scores.stream().filter(s -> trimester.equals(s.trimester())).toList();
    }

    /**
     * That trimester's total for this area, or null when the area has no row there at all — an
     * area that starts mid-year never had those trimesters, and a zero would say it failed them.
     *
     * <p>Returning the first match is safe rather than arbitrary: {@code academic_scores} carries
     * {@code uq_academic_score UNIQUE (id_course_enrollment, id_class_group, trimester)}, so a
     * second row for the same area and trimester cannot exist. Were it reachable, this would
     * disagree with {@link #averageOfTotals} — which counts every row — and the area's marks would
     * stop adding up to the average printed beside them. It is the constraint that rules that out,
     * not this loop, so dropping the constraint has to come back here.
     */
    private static BigDecimal totalOfTrimester(List<AcademicScore> rows, Integer trimester) {
        for (AcademicScore s : rows) {
            if (trimester.equals(s.trimester())) {
                return s.totalScore();
            }
        }
        return null;
    }

    /**
     * Mean of these rows' totals, or null when there are none. A row that exists but carries no
     * total counts as a zero and still occupies the denominator: the teacher opened that subject
     * and has not closed it, which is what the trimester sheet has always reported. Both views
     * share this one method so they can never disagree about a student.
     */
    private static BigDecimal averageOfTotals(List<AcademicScore> rows) {
        return mean(rows.stream()
            .map(s -> s.totalScore() == null ? BigDecimal.ZERO : s.totalScore())
            .toList());
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
