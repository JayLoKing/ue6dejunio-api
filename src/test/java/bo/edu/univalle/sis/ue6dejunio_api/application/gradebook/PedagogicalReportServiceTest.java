package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.PedagogicalReportService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.FailingStudentRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReport;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportNote;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportSheet;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IPedagogicalReportDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The informe pedagógico's derived half: section III's counts and section IV's list of who failed.
 *
 * <p>What these tests pin hardest is the rule the school's own sheet cannot express: a student
 * nobody marked is counted among the EFECTIVOS and in neither of the other two columns. The three
 * columns then fail to add up, and that gap is a teacher's reminder that marks are still missing —
 * folding those students into either column would hide exactly the thing the report exists to show.
 *
 * <p>Pure unit test: no DB, no Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class PedagogicalReportServiceTest {

    @Mock private IPedagogicalReportDomain reports;
    @Mock private ICourseService courseService;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;

    private PedagogicalReportService service;

    private UUID courseId;
    private UUID mathGroup;
    private UUID languageGroup;

    /** The roster the mocked port hands back, built up by {@link #student}. */
    private final List<CourseStudent> roster = new ArrayList<>();

    private final List<AcademicScore> scores = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service =
                new PedagogicalReportService(reports, courseService, enrollmentDomain, scoreDomain);
        courseId = UUID.randomUUID();
        mathGroup = UUID.randomUUID();
        languageGroup = UUID.randomUUID();
        roster.clear();
        scores.clear();
    }

    private void courseExists() {
        when(courseService.getById(courseId))
                .thenReturn(
                        new Course(
                                courseId,
                                1,
                                "Primero",
                                3,
                                "C",
                                7,
                                2026,
                                UUID.randomUUID(),
                                "Nora Arnez Veliz",
                                true));
    }

    /** Adds a student to the roster and answers with the enrolment id their marks hang off. */
    private UUID student(String names, String lastNames, String gender) {
        UUID enrollmentId = UUID.randomUUID();
        roster.add(
                new CourseStudent(
                        enrollmentId,
                        UUID.randomUUID(),
                        "RUDE-" + roster.size(),
                        "CI-" + roster.size(),
                        names,
                        lastNames,
                        "Effective",
                        gender));
        return enrollmentId;
    }

    private void mark(UUID enrollmentId, UUID classGroupId, String subject, String total) {
        scores.add(
                new AcademicScore(
                        UUID.randomUUID(),
                        enrollmentId,
                        classGroupId,
                        subject,
                        1,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal(total),
                        null,
                        null));
    }

    /** An area the teacher opened and never closed: a row with no total at all. */
    private void openedButUnmarked(UUID enrollmentId, UUID classGroupId, String subject) {
        scores.add(
                new AcademicScore(
                        UUID.randomUUID(),
                        enrollmentId,
                        classGroupId,
                        subject,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private void rosterAndScoresAreRead() {
        when(enrollmentDomain.activeStudentsByCourse(eq(courseId), any(PageQuery.class)))
                .thenAnswer(
                        call -> {
                            PageQuery query = call.getArgument(1);
                            return new PageResult<>(
                                    roster, query.page(), query.size(), roster.size());
                        });
        // Only when there is somebody to read marks for. An empty roster never reaches the score
        // port — an IN over no ids is a query worth nothing — and strict stubbing calls a stub that
        // was never used a mistake, which here it would be.
        if (!roster.isEmpty()) {
            when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(scores);
        }
    }

    private PedagogicalReportSheet sheetWithNoReportWritten() {
        when(reports.byCourseAndTrimester(courseId, 1)).thenReturn(Optional.empty());
        return service.sheet(courseId, 1);
    }

    @Test
    void sheet_carriesTheClassroomTheHeadingNeedsAndNothingTheSchoolAlreadyPublishes() {
        courseExists();
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.courseId()).isEqualTo(courseId);
        assertThat(sheet.gradeName()).isEqualTo("Primero");
        assertThat(sheet.parallelName()).isEqualTo("C");
        assertThat(sheet.year()).isEqualTo(2026);
        assertThat(sheet.homeroomTeacherName()).isEqualTo("Nora Arnez Veliz");
        assertThat(sheet.trimester()).isEqualTo(1);
    }

    /**
     * A report nobody has written still has sections I, III and IV. That is the blank form the
     * teacher opens: the statistics and the list of who failed are why they are about to write.
     */
    @Test
    void sheet_reportNeverWritten_saysSoInsteadOfLookingLikeAnEmptyOne() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        mark(ana, mathGroup, "Matematicas", "80");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.exists()).isFalse();
        assertThat(sheet.achievements()).isNull();
        assertThat(sheet.difficulties()).isNull();
        assertThat(sheet.updatedAt()).isNull();
        assertThat(sheet.stats().effective().total()).isEqualTo(1);
    }

    @Test
    void sheet_reportWritten_carriesTheProseTheTeacherTyped() {
        courseExists();
        rosterAndScoresAreRead();
        LocalDateTime savedAt = LocalDateTime.of(2026, 5, 20, 16, 30);
        when(reports.byCourseAndTrimester(courseId, 1))
                .thenReturn(
                        Optional.of(
                                new PedagogicalReport(
                                        UUID.randomUUID(),
                                        courseId,
                                        1,
                                        "Logros del curso",
                                        "Dificultades del curso",
                                        List.of(),
                                        savedAt)));

        PedagogicalReportSheet sheet = service.sheet(courseId, 1);

        assertThat(sheet.exists()).isTrue();
        assertThat(sheet.achievements()).isEqualTo("Logros del curso");
        assertThat(sheet.difficulties()).isEqualTo("Dificultades del curso");
        assertThat(sheet.updatedAt()).isEqualTo(savedAt);
    }

    /** 51 passes and 50 does not, the one number every document of this school agrees on. */
    @Test
    void sheet_fiftyOneIsTheCutAndFiftyIsNot() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        UUID bruno = student("Bruno", "Bermudez", "M");
        mark(ana, mathGroup, "Matematicas", "51");
        mark(bruno, mathGroup, "Matematicas", "50");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.stats().passed().total()).isEqualTo(1);
        assertThat(sheet.stats().failed().total()).isEqualTo(1);
        assertThat(sheet.failingStudents())
                .extracting(FailingStudentRow::fullName)
                .containsExactly("Bruno Bermudez");
    }

    @Test
    void sheet_splitsEveryColumnByGenderAndSharesItOverTheEffectiveRoster() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        UUID bruno = student("Bruno", "Bermudez", "M");
        UUID carla = student("Carla", "Caceres", "F");
        UUID dario = student("Dario", "Duran", "M");
        mark(ana, mathGroup, "Matematicas", "80");
        mark(bruno, mathGroup, "Matematicas", "70");
        mark(carla, mathGroup, "Matematicas", "40");
        mark(dario, mathGroup, "Matematicas", "30");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.stats().effective().male()).isEqualTo(2);
        assertThat(sheet.stats().effective().female()).isEqualTo(2);
        assertThat(sheet.stats().effective().total()).isEqualTo(4);
        assertThat(sheet.stats().effective().percentage()).isEqualByComparingTo("100.00");
        assertThat(sheet.stats().passed().male()).isEqualTo(1);
        assertThat(sheet.stats().passed().female()).isEqualTo(1);
        assertThat(sheet.stats().passed().percentage()).isEqualByComparingTo("50.00");
        assertThat(sheet.stats().failed().male()).isEqualTo(1);
        assertThat(sheet.stats().failed().female()).isEqualTo(1);
        assertThat(sheet.stats().failed().percentage()).isEqualByComparingTo("50.00");
    }

    /**
     * The rule the printed sheet has no column for. A student nobody marked is in EFECTIVOS and in
     * neither of the other two, so the columns come up short — and that shortfall is the marks
     * somebody still has to enter before this report can be signed.
     */
    @Test
    void sheet_studentNobodyMarked_countsAsEffectiveAndAsNeitherPassedNorFailed() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        student("Bruno", "Bermudez", "M");
        mark(ana, mathGroup, "Matematicas", "80");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.stats().effective().total()).isEqualTo(2);
        assertThat(sheet.stats().passed().total()).isEqualTo(1);
        assertThat(sheet.stats().failed().total()).isZero();
        assertThat(sheet.failingStudents()).isEmpty();
    }

    /**
     * An area opened and not closed carries no total. Read as a zero it would fail the student on a
     * subject nobody graded — and put them on section IV with a mark no teacher ever gave.
     */
    @Test
    void sheet_areaOpenedButNeverTotalled_isNotAZeroAndNotAFailure() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        openedButUnmarked(ana, mathGroup, "Matematicas");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.failingStudents()).isEmpty();
        assertThat(sheet.stats().failed().total()).isZero();
        assertThat(sheet.stats().passed().total()).isZero();
        assertThat(sheet.stats().effective().total()).isEqualTo(1);
    }

    /** One student, several failed areas, one row — the sheet stacks them inside a single cell. */
    @Test
    void sheet_studentFailingTwoAreas_getsOneRowCarryingBoth() {
        courseExists();
        UUID bruno = student("Bruno", "Bermudez", "M");
        mark(bruno, mathGroup, "Matematicas", "40");
        mark(bruno, languageGroup, "Lenguaje", "45");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.failingStudents()).hasSize(1);
        FailingStudentRow row = sheet.failingStudents().get(0);
        assertThat(row.number()).isEqualTo(1);
        assertThat(row.failedAreas())
                .extracting("subjectName")
                .containsExactly("Lenguaje", "Matematicas");
        assertThat(row.failedAreas())
                .extracting("mark")
                .containsExactly(new BigDecimal("45"), new BigDecimal("40"));
    }

    /** Only the areas that failed. A student who failed one subject did not fail the others. */
    @Test
    void sheet_failingRow_namesOnlyTheAreasThatFailed() {
        courseExists();
        UUID bruno = student("Bruno", "Bermudez", "M");
        mark(bruno, mathGroup, "Matematicas", "40");
        mark(bruno, languageGroup, "Lenguaje", "90");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.failingStudents().get(0).failedAreas())
                .extracting("subjectName")
                .containsExactly("Matematicas");
    }

    @Test
    void sheet_numbersTheFailingStudentsFromOneInRosterOrder() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        UUID bruno = student("Bruno", "Bermudez", "M");
        UUID carla = student("Carla", "Caceres", "F");
        mark(ana, mathGroup, "Matematicas", "20");
        mark(bruno, mathGroup, "Matematicas", "90");
        mark(carla, mathGroup, "Matematicas", "30");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.failingStudents())
                .extracting(FailingStudentRow::fullName)
                .containsExactly("Ana Alvarez", "Carla Caceres");
        assertThat(sheet.failingStudents())
                .extracting(FailingStudentRow::number)
                .containsExactly(1, 2);
    }

    @Test
    void sheet_carriesTheTeachersTwoColumnsOntoTheStudentTheyWereWrittenAbout() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        UUID bruno = student("Bruno", "Bermudez", "M");
        mark(ana, mathGroup, "Matematicas", "20");
        mark(bruno, mathGroup, "Matematicas", "30");
        rosterAndScoresAreRead();
        when(reports.byCourseAndTrimester(courseId, 1))
                .thenReturn(
                        Optional.of(
                                new PedagogicalReport(
                                        UUID.randomUUID(),
                                        courseId,
                                        1,
                                        null,
                                        null,
                                        List.of(
                                                new PedagogicalReportNote(
                                                        bruno,
                                                        "Refuerzo en horario alterno",
                                                        "Cuaderno")),
                                        LocalDateTime.of(2026, 5, 20, 16, 30))));

        PedagogicalReportSheet sheet = service.sheet(courseId, 1);

        FailingStudentRow anaRow = sheet.failingStudents().get(0);
        FailingStudentRow brunoRow = sheet.failingStudents().get(1);
        assertThat(anaRow.actions()).isNull();
        assertThat(anaRow.verificationSource()).isNull();
        assertThat(brunoRow.actions()).isEqualTo("Refuerzo en horario alterno");
        assertThat(brunoRow.verificationSource()).isEqualTo("Cuaderno");
    }

    /**
     * A paragraph about a student who is no longer failing is not printed and not deleted. The
     * failing set moves with every correction, and a teacher whose student passed on appeal should
     * find their text intact if the correction is undone.
     */
    @Test
    void sheet_noteAboutAStudentWhoIsNoLongerFailing_isNotPrintedAndNotLost() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        mark(ana, mathGroup, "Matematicas", "90");
        rosterAndScoresAreRead();
        when(reports.byCourseAndTrimester(courseId, 1))
                .thenReturn(
                        Optional.of(
                                new PedagogicalReport(
                                        UUID.randomUUID(),
                                        courseId,
                                        1,
                                        null,
                                        null,
                                        List.of(
                                                new PedagogicalReportNote(
                                                        ana, "Acciones de cuando reprobaba", null)),
                                        LocalDateTime.of(2026, 5, 20, 16, 30))));

        PedagogicalReportSheet sheet = service.sheet(courseId, 1);

        assertThat(sheet.failingStudents()).isEmpty();
        verify(reports, never()).save(any(), anyInt(), any());
    }

    /**
     * A course with nobody in it has no share to report. A printed 0% would read as "nobody
     * passed".
     */
    @Test
    void sheet_emptyClassroom_reportsNoShareRatherThanZeroPerCent() {
        courseExists();
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.stats().effective().total()).isZero();
        assertThat(sheet.stats().effective().percentage()).isNull();
        assertThat(sheet.stats().passed().percentage()).isNull();
        assertThat(sheet.stats().failed().percentage()).isNull();
    }

    /**
     * {@code students.gender} is nullable. The student is counted once in the total and in neither
     * of the two columns beside it, rather than guessed into one.
     */
    @Test
    void sheet_studentWithNoGenderOnRecord_countsInTheTotalAndInNeitherColumn() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", null);
        mark(ana, mathGroup, "Matematicas", "80");
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.stats().effective().total()).isEqualTo(1);
        assertThat(sheet.stats().effective().male()).isZero();
        assertThat(sheet.stats().effective().female()).isZero();
    }

    /** Only this trimester's marks. The sheet names one trimester in its own title. */
    @Test
    void sheet_readsOnlyTheTrimesterItWasAskedFor() {
        courseExists();
        UUID ana = student("Ana", "Alvarez", "F");
        scores.add(
                new AcademicScore(
                        UUID.randomUUID(),
                        ana,
                        mathGroup,
                        "Matematicas",
                        2,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("20"),
                        null,
                        null));
        rosterAndScoresAreRead();

        PedagogicalReportSheet sheet = sheetWithNoReportWritten();

        assertThat(sheet.failingStudents()).isEmpty();
        assertThat(sheet.stats().failed().total()).isZero();
        assertThat(sheet.stats().passed().total()).isZero();
    }

    @Test
    void save_writesTheDraftAndAnswersWithTheWholeSheet() {
        courseExists();
        UUID bruno = student("Bruno", "Bermudez", "M");
        mark(bruno, mathGroup, "Matematicas", "30");
        rosterAndScoresAreRead();
        when(enrollmentDomain.courseIdsByEnrollment(anyCollection()))
                .thenReturn(Map.of(bruno, courseId));
        PedagogicalReportDraft draft =
                new PedagogicalReportDraft(
                        "Logros",
                        "Dificultades",
                        List.of(new PedagogicalReportNote(bruno, "Refuerzo", "Cuaderno")));
        when(reports.save(eq(courseId), eq(1), any(PedagogicalReportDraft.class)))
                .thenReturn(
                        new PedagogicalReport(
                                UUID.randomUUID(),
                                courseId,
                                1,
                                "Logros",
                                "Dificultades",
                                List.of(new PedagogicalReportNote(bruno, "Refuerzo", "Cuaderno")),
                                LocalDateTime.of(2026, 5, 20, 16, 30)));

        PedagogicalReportSheet sheet = service.save(courseId, 1, draft);

        ArgumentCaptor<PedagogicalReportDraft> saved =
                ArgumentCaptor.forClass(PedagogicalReportDraft.class);
        verify(reports).save(eq(courseId), eq(1), saved.capture());
        assertThat(saved.getValue().achievements()).isEqualTo("Logros");
        assertThat(sheet.exists()).isTrue();
        assertThat(sheet.achievements()).isEqualTo("Logros");
        assertThat(sheet.failingStudents()).hasSize(1);
        assertThat(sheet.failingStudents().get(0).actions()).isEqualTo("Refuerzo");
    }

    /**
     * The foreign key points at any enrolment in the school, so nothing below stops a teacher
     * filing a paragraph about a child they do not teach. It would never be printed — section IV is
     * drawn from this course's roster — which is why it is refused rather than quietly stored.
     */
    @Test
    void save_noteAboutAnotherClassroomsStudent_isRefused() {
        courseExists();
        UUID stranger = UUID.randomUUID();
        when(enrollmentDomain.courseIdsByEnrollment(anyCollection()))
                .thenReturn(Map.of(stranger, UUID.randomUUID()));

        assertThatThrownBy(
                        () ->
                                service.save(
                                        courseId,
                                        1,
                                        new PedagogicalReportDraft(
                                                null,
                                                null,
                                                List.of(
                                                        new PedagogicalReportNote(
                                                                stranger, "Acciones", null)))))
                .isInstanceOf(ValidationException.class);

        verify(reports, never()).save(any(), anyInt(), any());
    }

    /** An id that names no enrolment at all is refused with the rest: nothing would be stored. */
    @Test
    void save_noteAgainstAnEnrolmentThatDoesNotExist_isRefused() {
        courseExists();
        when(enrollmentDomain.courseIdsByEnrollment(anyCollection())).thenReturn(Map.of());

        assertThatThrownBy(
                        () ->
                                service.save(
                                        courseId,
                                        1,
                                        new PedagogicalReportDraft(
                                                null,
                                                null,
                                                List.of(
                                                        new PedagogicalReportNote(
                                                                UUID.randomUUID(),
                                                                "Acciones",
                                                                null)))))
                .isInstanceOf(ValidationException.class);

        verify(reports, never()).save(any(), anyInt(), any());
    }

    /** A draft with no paragraphs asks nothing of the enrolments, so it costs no query. */
    @Test
    void save_draftWithNoNotes_asksTheEnrolmentsNothing() {
        courseExists();
        rosterAndScoresAreRead();
        when(reports.save(eq(courseId), eq(1), any(PedagogicalReportDraft.class)))
                .thenReturn(
                        new PedagogicalReport(
                                UUID.randomUUID(),
                                courseId,
                                1,
                                "Sólo logros",
                                null,
                                List.of(),
                                LocalDateTime.of(2026, 5, 20, 16, 30)));

        service.save(courseId, 1, new PedagogicalReportDraft("Sólo logros", null, List.of()));

        verify(enrollmentDomain, never()).courseIdsByEnrollment(anyCollection());
    }
}
