package bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroupField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualSubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseOverview;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.GradeInWords;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.HonorRollEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.KnowledgeFieldRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PassingMark;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentReportCard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentTrimesterSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.SubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.TrimesterOutcome;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class GradebookService implements IGradebookService {

    /**
     * How much of a roster one read brings back while a podium is being built. Big enough that a
     * real classroom is one page, and the loop around it is what keeps a larger one correct.
     */
    private static final int ROSTER_PAGE_SIZE = 200;

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
    public List<HonorRollEntry> honorRoll(UUID courseId, int places) {
        return podiumOf(courseService.getById(courseId), places);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HonorRollEntry> institutionHonorRoll(Integer academicYearId, int places) {
        // Without a gestión the course query answers every year at once, and the podium would rank
        // a student of 2024 against one of 2026 — two years the school never compared. It is also
        // what makes the paged course read sound: see ICourseService.allOfYear.
        if (academicYearId == null) {
            throw new ValidationException("A school-wide honour roll needs the gestión it belongs to");
        }
        /*
         * The best of each course first, then the best of those. Taking `places` from every course
         * before merging is exact and not an approximation: a student who is eleventh in their own
         * classroom already has ten ahead of them, so they cannot be in the school's top ten. It is
         * also what keeps this bounded — the whole school never lands in memory at once, only a few
         * rows per course.
         */
        List<HonorRollEntry> best = new ArrayList<>();
        for (Course course : courseService.allOfYear(academicYearId)) {
            best.addAll(podiumOf(course, places));
        }
        return ranked(best, places);
    }

    /** One course's podium, read from the whole roster rather than from its first page. */
    private List<HonorRollEntry> podiumOf(Course course, int places) {
        List<HonorRollEntry> candidates = new ArrayList<>();
        for (StudentAnnualSummary annual : annualSummariesOf(course.id())) {
            // Nothing graded is not a bad year: it is a year nobody judged, and last place would
            // say otherwise. They hold no place at all.
            if (annual.finalAverage() != null) {
                candidates.add(new HonorRollEntry(0, annual.courseEnrollmentId(), annual.studentId(),
                    annual.fullName(), course.id(), course.gradeName(), course.parallelName(),
                    annual.finalAverage()));
            }
        }
        return ranked(candidates, places);
    }

    /**
     * Best average first, cut at {@code places}, numbered from one.
     *
     * <p>The name breaks a tie. Two students on the same average would otherwise come out in
     * whatever order the batch answered in, and the school would print two different podiums from
     * one year. It breaks on the name the podium itself shows, so what decides the order is
     * something the reader can see.
     */
    private static List<HonorRollEntry> ranked(List<HonorRollEntry> entries, int places) {
        List<HonorRollEntry> sorted = entries.stream()
            .sorted(Comparator.comparing(HonorRollEntry::finalAverage).reversed()
                .thenComparing(HonorRollEntry::fullName))
            .limit(places)
            .toList();
        return IntStream.range(0, sorted.size())
            .mapToObj(i -> {
                HonorRollEntry e = sorted.get(i);
                return new HonorRollEntry(i + 1, e.courseEnrollmentId(), e.studentId(), e.fullName(),
                    e.courseId(), e.gradeName(), e.parallelName(), e.finalAverage());
            })
            .toList();
    }

    /**
     * Every student of the course with their year worked out, read page by page.
     *
     * <p>Paged rather than asked for in one go, because a podium built from the first page only is
     * a podium that silently drops the best student of a large course. Each page still costs the
     * same two queries the annual centralizer pays — the roster and one batched load of its scores.
     *
     * <p>The ordering is what makes reading it page by page sound, and it has to be <em>total</em>.
     * {@code LIMIT/OFFSET} with no {@code ORDER BY} lets Postgres hand the same row back on two
     * pages and never hand back another; the loop would then stop on an inflated count with a
     * student unread. The name alone is not enough either — two students who share one would tie,
     * and a tie is where the order is free to move again. The enrollment id closes it.
     *
     * <p>Every enrolment, not only the ones still in force. A student who withdrew in November
     * still earned the marks they earned, and the libreta and the centralizer both count them; a
     * podium that quietly disagreed with the sheets beside it would be the odd one out.
     */
    private List<StudentAnnualSummary> annualSummariesOf(UUID courseId) {
        List<StudentAnnualSummary> all = new ArrayList<>();
        int pageIndex = 0;
        while (true) {
            PageResult<CourseStudent> page = enrollmentDomain
                .studentsByCourse(courseId, PageQuery.of(pageIndex, ROSTER_PAGE_SIZE,
                    SortField.asc("student.lastNames"), SortField.asc("student.names"),
                    SortField.asc("id")));
            Map<UUID, List<AcademicScore>> grouped = scoresByEnrollment(page);
            for (CourseStudent cs : page.content()) {
                all.add(buildAnnualSummary(cs.courseEnrollmentId(), cs.studentId(), cs.fullName(),
                    grouped.getOrDefault(cs.courseEnrollmentId(), List.of())));
            }
            if (readEverything(all.size(), page.content().size(), page.totalElements())) {
                return all;
            }
            pageIndex++;
        }
    }

    /**
     * Whether there is anything left to page through.
     *
     * <p>It asks how many rows are still missing rather than how many pages the store reports,
     * because {@code totalPages} is derived from the size that was asked for and answers one for
     * any set that fits in a single page — which is every set, until it is not. The empty page is
     * the other exit: a store that keeps answering nothing would otherwise be read forever.
     */
    private static boolean readEverything(int gathered, int lastPageSize, long totalElements) {
        return lastPageSize == 0 || gathered >= totalElements;
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

    @Override
    @Transactional(readOnly = true)
    public StudentReportCard reportCard(UUID courseEnrollmentId) {
        CourseStudent cs = enrollmentDomain.courseStudentById(courseEnrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId));
        UUID courseId = enrollmentDomain.courseOfEnrollment(courseEnrollmentId);
        Course course = courseService.getById(courseId);
        StudentAnnualSummary annual = buildAnnualSummary(courseEnrollmentId, cs.studentId(),
            cs.fullName(), scoreDomain.findByCourseEnrollment(courseEnrollmentId));

        Map<UUID, ClassGroupField> fieldOfClassGroup = classGroupDomain
            .knowledgeFieldsByCourse(courseId).stream()
            .collect(Collectors.toMap(ClassGroupField::classGroupId, f -> f, (first, dup) -> first));

        return new StudentReportCard(courseEnrollmentId, cs.studentId(), cs.rudeCode(),
            cs.fullName(), course.gradeName(), course.parallelName(), course.year(),
            fieldRows(annual.subjects(), fieldOfClassGroup),
            annual.trimester1Average(), annual.trimester2Average(), annual.trimester3Average(),
            annual.finalAverage(), GradeInWords.of(annual.finalAverage()),
            outcomes(annual.subjects()));
    }

    /**
     * The student's areas under their field of knowledge, in the order the school's sheet reads.
     *
     * <p>A field the student has no marked area in never appears: a heading over nothing reads as
     * a subject whose marks went missing. An area whose class group is no longer active has no
     * field to hang under, and is kept in a trailing row rather than dropped — those marks were
     * given, and a libreta that quietly loses a subject is worse than one with an unnamed row.
     *
     * <p>Grouped by the field's id and never by its {@code displayOrder}. Nothing holds the order
     * unique — the Director types it in by hand — and two fields sharing one would otherwise fold
     * into a single row under whichever name arrived first, printing a heading over somebody
     * else's areas and dropping a field off the document with nothing to show it had happened.
     */
    private static List<KnowledgeFieldRow> fieldRows(List<AnnualSubjectScore> subjects,
                                                     Map<UUID, ClassGroupField> fieldOfClassGroup) {
        Map<Integer, List<AnnualSubjectScore>> areasOfField = new LinkedHashMap<>();
        Map<Integer, ClassGroupField> fieldById = new LinkedHashMap<>();
        for (AnnualSubjectScore subject : subjects) {
            ClassGroupField field = fieldOfClassGroup.get(subject.classGroupId());
            Integer fieldId = field == null ? null : field.fieldId();
            areasOfField.computeIfAbsent(fieldId, k -> new ArrayList<>()).add(subject);
            if (field != null) {
                fieldById.putIfAbsent(fieldId, field);
            }
        }
        return areasOfField.entrySet().stream()
            .map(e -> {
                ClassGroupField field = fieldById.get(e.getKey());
                return new KnowledgeFieldRow(field == null ? null : field.fieldName(),
                    field == null ? null : field.displayOrder(), e.getValue());
            })
            // Two fields sharing an order keep their own rows; the tie is broken by name so the
            // document at least reads the same way twice.
            .sorted(Comparator
                .comparing(KnowledgeFieldRow::displayOrder,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(KnowledgeFieldRow::fieldName,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /** Always three, one per trimester, even before the year is over. */
    private static List<TrimesterOutcome> outcomes(List<AnnualSubjectScore> subjects) {
        List<TrimesterOutcome> outcomes = new ArrayList<>(3);
        for (int trimester = 1; trimester <= 3; trimester++) {
            int passed = 0;
            int failed = 0;
            for (AnnualSubjectScore subject : subjects) {
                BigDecimal mark = markOf(subject, trimester);
                if (mark == null) {
                    // Never marked that trimester. Counted in neither: calling it failed would tell
                    // a parent their child failed a subject nobody judged.
                    continue;
                }
                if (PassingMark.reachedBy(mark)) {
                    passed++;
                } else {
                    failed++;
                }
            }
            outcomes.add(new TrimesterOutcome(trimester, passed, failed));
        }
        return List.copyOf(outcomes);
    }

    private static BigDecimal markOf(AnnualSubjectScore subject, int trimester) {
        return switch (trimester) {
            case 1 -> subject.trimester1();
            case 2 -> subject.trimester2();
            default -> subject.trimester3();
        };
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
