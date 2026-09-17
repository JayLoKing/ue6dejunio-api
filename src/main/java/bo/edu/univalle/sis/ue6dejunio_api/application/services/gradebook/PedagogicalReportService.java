package bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.FailedArea;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.FailingStudentRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.GenderTally;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PassingMark;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReport;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportNote;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportSheet;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportStats;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IPedagogicalReportDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IPedagogicalReportService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The informe pedagógico: sections I, III and the marks of IV derived from the record, sections II
 * and the two rightmost columns of IV read from what the teacher wrote.
 *
 * <p>Its own service rather than another method on {@code GradebookService}. Everything there
 * answers a question about marks already entered and owns no table; this owns a document, its write
 * path, and the rule about who may sign it.
 */
@Service
public class PedagogicalReportService implements IPedagogicalReportService {

    /**
     * How much of the roster one read brings back. A real classroom is one page; the loop around it
     * is what keeps a larger one whole, and a sheet built from page one alone would leave students
     * off both the statistics and the list of who failed.
     */
    private static final int ROSTER_PAGE_SIZE = 200;

    /** {@code students.gender}, as {@code V3} constrains it. Nullable, which section III respects. */
    private static final String MALE = "M";
    private static final String FEMALE = "F";

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final IPedagogicalReportDomain reports;
    private final ICourseService courseService;
    private final ICourseEnrollmentDomain enrollmentDomain;
    private final IScoreDomain scoreDomain;

    public PedagogicalReportService(IPedagogicalReportDomain reports,
                                    ICourseService courseService,
                                    ICourseEnrollmentDomain enrollmentDomain,
                                    IScoreDomain scoreDomain) {
        this.reports = reports;
        this.courseService = courseService;
        this.enrollmentDomain = enrollmentDomain;
        this.scoreDomain = scoreDomain;
    }

    @Override
    @Transactional(readOnly = true)
    public PedagogicalReportSheet sheet(UUID courseId, int trimester) {
        Course course = courseService.getById(courseId);
        List<StudentTrimester> roster = rosterOf(courseId, trimester);
        Optional<PedagogicalReport> written = reports.byCourseAndTrimester(courseId, trimester);
        return render(course, trimester, roster, written);
    }

    @Override
    @Transactional
    public PedagogicalReportSheet save(UUID courseId, int trimester, PedagogicalReportDraft draft) {
        Course course = courseService.getById(courseId);
        rejectNotesFromAnotherClassroom(courseId, draft);
        PedagogicalReport written = reports.save(courseId, trimester, draft);
        // Read after the write, not before: the statistics and the list of who failed are what the
        // teacher is looking at while they type, and a mark corrected in between would leave the
        // screen showing a sheet the save did not produce.
        List<StudentTrimester> roster = rosterOf(courseId, trimester);
        return render(course, trimester, roster, Optional.of(written));
    }

    /**
     * Refuses a save carrying a paragraph about a student of another classroom.
     *
     * <p>The foreign key alone does not stop this: {@code pedagogical_report_failures} points at
     * any enrolment in the school, so a teacher could file a note about a child they do not teach
     * against their own report. It would never appear on any sheet — section IV is drawn from this
     * course's roster — which is exactly what makes it worth refusing loudly instead of storing a
     * row nobody will ever see again.
     *
     * <p>One query for the whole draft, so the check costs the same whether it carries one
     * paragraph or thirty. An id that resolves to nothing is refused with the rest: it names no
     * enrolment at all, and a save that dropped it silently would report success for work that was
     * not stored.
     */
    private void rejectNotesFromAnotherClassroom(UUID courseId, PedagogicalReportDraft draft) {
        List<PedagogicalReportNote> notes = draft.notes();
        if (notes == null || notes.isEmpty()) {
            return;
        }
        if (notes.stream().anyMatch(n -> n.courseEnrollmentId() == null)) {
            throw new ValidationException(
                "Cada observación del informe tiene que nombrar la matrícula del estudiante");
        }
        List<UUID> enrollmentIds = notes.stream()
            .map(PedagogicalReportNote::courseEnrollmentId)
            .distinct()
            .toList();
        Map<UUID, UUID> courseOf = enrollmentDomain.courseIdsByEnrollment(enrollmentIds);
        boolean foreign = enrollmentIds.stream()
            .anyMatch(id -> !courseId.equals(courseOf.get(id)));
        if (foreign) {
            throw new ValidationException(
                "El informe sólo puede describir a estudiantes matriculados en el curso");
        }
    }

    /**
     * Every effective student of the course with their trimester worked out, read page by page.
     *
     * <p>The effective roster and not every enrolment ever filed, which is the one place this
     * report parts company with the libreta and the centralizador. Those are year-end academic
     * records and a withdrawn student still owns the marks they earned. This one is a count of who
     * is sitting the classroom — the sheet's own word is EFECTIVOS — and a child who left in April
     * is not among them.
     *
     * <p>Paged with a total order for the same reason the podium is: {@code LIMIT/OFFSET} with no
     * {@code ORDER BY} lets Postgres hand one row back twice and another never, and the loop would
     * then stop on a full count with a student unread. The name alone does not close it — two
     * students can share one — so the enrolment id follows.
     */
    private List<StudentTrimester> rosterOf(UUID courseId, int trimester) {
        List<StudentTrimester> roster = new ArrayList<>();
        int pageIndex = 0;
        while (true) {
            PageResult<CourseStudent> page = enrollmentDomain.activeStudentsByCourse(courseId,
                PageQuery.of(pageIndex, ROSTER_PAGE_SIZE,
                    SortField.asc("student.lastNames"), SortField.asc("student.names"),
                    SortField.asc("id")));
            Map<UUID, List<AcademicScore>> scores = scoresByEnrollment(page, trimester);
            for (CourseStudent cs : page.content()) {
                roster.add(new StudentTrimester(cs,
                    areasOf(scores.getOrDefault(cs.courseEnrollmentId(), List.of()))));
            }
            if (readEverything(roster.size(), page.content().size(), page.totalElements())) {
                return roster;
            }
            pageIndex++;
        }
    }

    /** Whether there is anything left to page through. Mirrors the gradebook's own loop guard. */
    private static boolean readEverything(int gathered, int lastPageSize, long totalElements) {
        return lastPageSize == 0 || gathered >= totalElements;
    }

    /** One batched load for the whole page, then in-memory grouping — never a query per student. */
    private Map<UUID, List<AcademicScore>> scoresByEnrollment(PageResult<CourseStudent> page,
                                                              int trimester) {
        List<UUID> ids = page.content().stream().map(CourseStudent::courseEnrollmentId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return scoreDomain.findByCourseEnrollmentIn(ids).stream()
            .filter(s -> Integer.valueOf(trimester).equals(s.trimester()))
            .collect(Collectors.groupingBy(AcademicScore::courseEnrollmentId));
    }

    /**
     * One student's marked areas for the trimester, ordered the way the sheet's cell reads them.
     *
     * <p>An area with no total is dropped rather than read as a zero. The row exists because the
     * teacher opened the subject, not because they closed it, and counting it as a nought would put
     * a failed area on the child's line that nobody gave them.
     *
     * <p>The order is the area's name, with the class group breaking a tie between two areas that
     * share one. The cell stacks several areas on several lines, and an order left to whichever
     * order the batch answered in would print the same child's marks differently on two readings.
     */
    private static List<MarkedArea> areasOf(List<AcademicScore> scoresOfTrimester) {
        return scoresOfTrimester.stream()
            .filter(s -> s.totalScore() != null)
            .map(s -> new MarkedArea(s.classGroupId(), s.subjectName(), s.totalScore()))
            .sorted(Comparator
                .comparing(MarkedArea::subjectName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(a -> a.classGroupId().toString()))
            .toList();
    }

    private PedagogicalReportSheet render(Course course, int trimester, List<StudentTrimester> roster,
                                          Optional<PedagogicalReport> written) {
        Map<UUID, PedagogicalReportNote> noteOf = written
            .map(PedagogicalReportService::notesByEnrollment)
            .orElseGet(Map::of);
        return new PedagogicalReportSheet(
            course.id(), course.gradeName(), course.parallelName(), course.year(),
            course.homeroomTeacherName(), trimester,
            written.isPresent(),
            written.map(PedagogicalReport::achievements).orElse(null),
            written.map(PedagogicalReport::difficulties).orElse(null),
            statsOf(roster),
            failingRows(roster, noteOf),
            written.map(PedagogicalReport::updatedAt).orElse(null));
    }

    private static Map<UUID, PedagogicalReportNote> notesByEnrollment(PedagogicalReport report) {
        Map<UUID, PedagogicalReportNote> byEnrollment = new LinkedHashMap<>();
        for (PedagogicalReportNote note : report.notes()) {
            byEnrollment.put(note.courseEnrollmentId(), note);
        }
        return byEnrollment;
    }

    /**
     * Section III. Three columns over the same effective roster, each split by gender.
     *
     * <p>The three do not have to add up, and {@link PedagogicalReportStats} says why: a student
     * with no mark at all this trimester was judged by nobody and belongs in neither the passed nor
     * the failed column. They stay in EFECTIVOS, where the gap between it and the other two is a
     * teacher's reminder that marks are still missing.
     */
    private static PedagogicalReportStats statsOf(List<StudentTrimester> roster) {
        List<CourseStudent> effective = roster.stream().map(StudentTrimester::student).toList();
        List<CourseStudent> failed = roster.stream()
            .filter(StudentTrimester::failedSomething)
            .map(StudentTrimester::student)
            .toList();
        List<CourseStudent> passed = roster.stream()
            .filter(s -> s.wasJudged() && !s.failedSomething())
            .map(StudentTrimester::student)
            .toList();
        int total = effective.size();
        return new PedagogicalReportStats(
            tally(effective, total), tally(passed, total), tally(failed, total));
    }

    /**
     * One column of section III.
     *
     * <p>{@code male + female} can come out under {@code total}: {@code students.gender} is
     * nullable, and a student enrolled before anyone recorded theirs is counted once in the total
     * and in neither column. Guessing would put a child under a heading the school never wrote.
     */
    private static GenderTally tally(List<CourseStudent> students, int effectiveTotal) {
        int male = 0;
        int female = 0;
        for (CourseStudent student : students) {
            if (MALE.equals(student.gender())) {
                male++;
            } else if (FEMALE.equals(student.gender())) {
                female++;
            }
        }
        return new GenderTally(male, female, students.size(), share(students.size(), effectiveTotal));
    }

    /**
     * The column's share of the effective roster, two decimals.
     *
     * <p>Null and not zero when there is nobody effective. A course with no students has no share
     * to report, and a printed 0% would read as "nobody passed" about a classroom that has no one
     * to pass.
     */
    private static BigDecimal share(int count, int effectiveTotal) {
        if (effectiveTotal == 0) {
            return null;
        }
        return BigDecimal.valueOf(count)
            .multiply(HUNDRED)
            .divide(BigDecimal.valueOf(effectiveTotal), 2, RoundingMode.HALF_UP);
    }

    /**
     * Section IV: the students who failed something, in roster order, numbered from one.
     *
     * <p>Roster order and not worst-first. The school's sheet is a register of a classroom, and it
     * reads the way every other list of these children reads — by surname — so a teacher can follow
     * it against the centralizador beside it.
     *
     * <p>A stored paragraph about a student who is not failing is not printed and not deleted. The
     * failing set moves every time a mark is corrected, and a teacher whose student passed on
     * appeal should find their text intact if the correction is undone.
     */
    private static List<FailingStudentRow> failingRows(List<StudentTrimester> roster,
                                                       Map<UUID, PedagogicalReportNote> noteOf) {
        List<FailingStudentRow> rows = new ArrayList<>();
        int number = 1;
        for (StudentTrimester entry : roster) {
            if (!entry.failedSomething()) {
                continue;
            }
            CourseStudent student = entry.student();
            PedagogicalReportNote note = noteOf.get(student.courseEnrollmentId());
            rows.add(new FailingStudentRow(number++, student.courseEnrollmentId(),
                student.studentId(), student.fullName(), entry.failedAreas(),
                note == null ? null : note.actions(),
                note == null ? null : note.verificationSource()));
        }
        return List.copyOf(rows);
    }

    /** One area with a total, before anything has decided whether it passed. */
    private record MarkedArea(UUID classGroupId, String subjectName, BigDecimal mark) {}

    /** One student of the roster with the areas they were marked in this trimester. */
    private record StudentTrimester(CourseStudent student, List<MarkedArea> markedAreas) {

        /** Whether anybody marked this student at all. Nobody did is not the same as passing. */
        boolean wasJudged() {
            return !markedAreas.isEmpty();
        }

        boolean failedSomething() {
            return markedAreas.stream().anyMatch(a -> !PassingMark.reachedBy(a.mark()));
        }

        List<FailedArea> failedAreas() {
            return markedAreas.stream()
                .filter(a -> !PassingMark.reachedBy(a.mark()))
                .map(a -> new FailedArea(a.classGroupId(), a.subjectName(), a.mark()))
                .toList();
        }
    }
}
