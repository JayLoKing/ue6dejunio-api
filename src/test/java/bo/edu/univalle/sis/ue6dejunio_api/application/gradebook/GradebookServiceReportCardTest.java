package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroupField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.KnowledgeFieldRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentReportCard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.TrimesterOutcome;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The libreta: one student's year laid out the way their report card prints it, grouped by field
 * of knowledge and closing with the annual average in figures and in words.
 *
 * <p>Almost none of this is new arithmetic — the per-area marks come from the same code the annual
 * centralizer uses. What is new, and what these tests pin, is the grouping, the order the sheet
 * reads in, and the counts of areas passed and failed per trimester.
 */
@ExtendWith(MockitoExtension.class)
class GradebookServiceReportCardTest {

    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ICourseService courseService;
    @Mock private IClassGroupDomain classGroupDomain;

    private GradebookService service;

    private UUID courseId;
    private UUID enrollment;
    private UUID student;
    private UUID cgLanguage;
    private UUID cgMath;
    private UUID cgNatural;

    @BeforeEach
    void setUp() {
        service = new GradebookService(enrollmentDomain, scoreDomain, attendanceDomain,
            courseService, classGroupDomain);
        courseId = UUID.randomUUID();
        enrollment = UUID.randomUUID();
        student = UUID.randomUUID();
        cgLanguage = UUID.randomUUID();
        cgMath = UUID.randomUUID();
        cgNatural = UUID.randomUUID();
    }

    private void enrolled() {
        when(enrollmentDomain.courseStudentById(enrollment)).thenReturn(Optional.of(
            new CourseStudent(enrollment, student, "7042002820239055", "12345678",
                "NELSY", "AIZA ARICOMA", "Effective", "F")));
        lenient().when(enrollmentDomain.courseOfEnrollment(enrollment)).thenReturn(courseId);
        lenient().when(courseService.getById(courseId)).thenReturn(new Course(courseId,
            2, "2do", 3, "C", 1, 2026, UUID.randomUUID(), "Nora Arnez Veliz", true));
    }

    /** The three fields of knowledge, deliberately handed back out of sheet order. */
    private void fieldsOfTheCourse() {
        lenient().when(classGroupDomain.knowledgeFieldsByCourse(courseId)).thenReturn(List.of(
            new ClassGroupField(cgNatural, 3, "Vida Tierra y Territorio", 3),
            new ClassGroupField(cgLanguage, 1, "Comunidad y Sociedad", 1),
            new ClassGroupField(cgMath, 2, "Ciencia Tecnología y Producción", 2)));
    }

    private void scored(AcademicScore... scores) {
        lenient().when(scoreDomain.findByCourseEnrollment(enrollment)).thenReturn(List.of(scores));
    }

    private AcademicScore score(UUID classGroupId, String subject, Integer trimester, String total) {
        return new AcademicScore(UUID.randomUUID(), enrollment, classGroupId, subject, trimester,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            total == null ? null : new BigDecimal(total), null, null);
    }

    @Test
    void reportCard_headsItselfWithTheStudentTheSheetNames() {
        enrolled();
        fieldsOfTheCourse();
        scored();

        StudentReportCard card = service.reportCard(enrollment);

        // The RUDE is what the libreta identifies a student by, above their own name.
        assertThat(card.rudeCode()).isEqualTo("7042002820239055");
        assertThat(card.fullName()).isEqualTo("NELSY AIZA ARICOMA");
        assertThat(card.gradeName()).isEqualTo("2do");
        assertThat(card.parallelName()).isEqualTo("C");
        assertThat(card.year()).isEqualTo(2026);
    }

    @Test
    void reportCard_groupsTheAreasUnderTheirFieldOfKnowledge() {
        enrolled();
        fieldsOfTheCourse();
        scored(
            score(cgLanguage, "Comunicación y Lenguajes", 1, "80"),
            score(cgMath, "Matemática", 1, "70"),
            score(cgNatural, "Ciencias Naturales", 1, "60"));

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.fields()).extracting(KnowledgeFieldRow::fieldName)
            .containsExactly("Comunidad y Sociedad", "Ciencia Tecnología y Producción",
                "Vida Tierra y Territorio");
        assertThat(card.fields().get(0).subjects()).extracting("subjectName")
            .containsExactly("Comunicación y Lenguajes");
    }

    @Test
    void reportCard_ordersTheFieldsBySheetOrder_notByHowTheQueryAnswered() {
        enrolled();
        // Handed back 3, 1, 2 on purpose: the order a query answers in is not the sheet's, and a
        // libreta whose rows move between two prints cannot be checked against the paper one.
        fieldsOfTheCourse();
        scored(
            score(cgNatural, "Ciencias Naturales", 1, "60"),
            score(cgMath, "Matemática", 1, "70"),
            score(cgLanguage, "Comunicación y Lenguajes", 1, "80"));

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.fields()).extracting(KnowledgeFieldRow::displayOrder)
            .containsExactly(1, 2, 3);
    }

    @Test
    void reportCard_twoFieldsSharingADisplayOrder_keepTheirOwnRows() {
        enrolled();
        // Nothing holds display_order unique — the Director types it in by hand — so two fields
        // can carry the same one. Grouping by the order would fold both into a single row under
        // whichever name arrived first, printing a heading over somebody else's areas and dropping
        // a field off a document a parent signs, with nothing to show it had happened.
        when(classGroupDomain.knowledgeFieldsByCourse(courseId)).thenReturn(List.of(
            new ClassGroupField(cgLanguage, 1, "Comunidad y Sociedad", 1),
            new ClassGroupField(cgMath, 2, "Ciencia Tecnología y Producción", 1)));
        scored(
            score(cgLanguage, "Comunicación y Lenguajes", 1, "80"),
            score(cgMath, "Matemática", 1, "70"));

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.fields()).hasSize(2);
        assertThat(card.fields()).extracting(KnowledgeFieldRow::fieldName)
            .containsExactly("Ciencia Tecnología y Producción", "Comunidad y Sociedad");
        assertThat(card.fields().get(0).subjects()).extracting("subjectName")
            .containsExactly("Matemática");
        assertThat(card.fields().get(1).subjects()).extracting("subjectName")
            .containsExactly("Comunicación y Lenguajes");
    }

    @Test
    void reportCard_anAreaWhoseClassGroupWasDeactivated_keepsItsMarksInATrailingRow() {
        enrolled();
        // Only the language group is still active, so the maths one has no field to hang under.
        // Those marks were given: a libreta that quietly loses a subject is worse than one that
        // prints an unnamed row.
        when(classGroupDomain.knowledgeFieldsByCourse(courseId)).thenReturn(List.of(
            new ClassGroupField(cgLanguage, 1, "Comunidad y Sociedad", 1)));
        scored(
            score(cgLanguage, "Comunicación y Lenguajes", 1, "80"),
            score(cgMath, "Matemática", 1, "70"));

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.fields()).hasSize(2);
        assertThat(card.fields().get(0).fieldName()).isEqualTo("Comunidad y Sociedad");
        KnowledgeFieldRow trailing = card.fields().get(1);
        assertThat(trailing.fieldName()).isNull();
        assertThat(trailing.subjects()).extracting("subjectName").containsExactly("Matemática");
    }

    @Test
    void reportCard_aFieldWithNoMarkedSubject_doesNotOpenAnEmptyRow() {
        enrolled();
        fieldsOfTheCourse();
        scored(score(cgLanguage, "Comunicación y Lenguajes", 1, "80"));

        StudentReportCard card = service.reportCard(enrollment);

        // Only the field the student actually has a mark in. An empty field prints a heading over
        // nothing, which reads as a subject whose marks went missing.
        assertThat(card.fields()).extracting(KnowledgeFieldRow::fieldName)
            .containsExactly("Comunidad y Sociedad");
    }

    @Test
    void reportCard_countsAreasPassedAndFailedPerTrimester() {
        enrolled();
        fieldsOfTheCourse();
        scored(
            score(cgLanguage, "Comunicación y Lenguajes", 1, "80"),
            score(cgMath, "Matemática", 1, "50"),
            score(cgNatural, "Ciencias Naturales", 1, "51"),
            score(cgLanguage, "Comunicación y Lenguajes", 2, "40"));

        StudentReportCard card = service.reportCard(enrollment);

        // 51 is the passing mark, so 51 passes and 50 does not.
        TrimesterOutcome first = card.trimesterOutcomes().get(0);
        assertThat(first.trimester()).isEqualTo(1);
        assertThat(first.passedAreas()).isEqualTo(2);
        assertThat(first.failedAreas()).isEqualTo(1);

        TrimesterOutcome second = card.trimesterOutcomes().get(1);
        assertThat(second.passedAreas()).isZero();
        assertThat(second.failedAreas()).isEqualTo(1);
    }

    @Test
    void reportCard_anAreaNeverGradedThatTrimester_countsAsNeitherPassedNorFailed() {
        enrolled();
        fieldsOfTheCourse();
        scored(score(cgLanguage, "Comunicación y Lenguajes", 1, "80"));

        StudentReportCard card = service.reportCard(enrollment);

        // Two areas have no row in the third trimester. Counting them as failed would tell a
        // parent their child failed subjects nobody marked.
        TrimesterOutcome third = card.trimesterOutcomes().get(2);
        assertThat(third.passedAreas()).isZero();
        assertThat(third.failedAreas()).isZero();
    }

    @Test
    void reportCard_alwaysCarriesThreeTrimesterOutcomes_evenBeforeTheYearIsOver() {
        enrolled();
        fieldsOfTheCourse();
        scored();

        assertThat(service.reportCard(enrollment).trimesterOutcomes())
            .extracting(TrimesterOutcome::trimester)
            .containsExactly(1, 2, 3);
    }

    @Test
    void reportCard_spellsTheAnnualAverageOutBesideTheNumeral() {
        enrolled();
        fieldsOfTheCourse();
        scored(
            score(cgLanguage, "Comunicación y Lenguajes", 1, "80"),
            score(cgLanguage, "Comunicación y Lenguajes", 2, "80"),
            score(cgLanguage, "Comunicación y Lenguajes", 3, "80"));

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.finalAverage()).isEqualByComparingTo("80.00");
        assertThat(card.finalAverageInWords()).isEqualTo("Ochenta");
    }

    @Test
    void reportCard_nothingGradedYet_leavesTheLiteralBlankRatherThanSpellingZero() {
        enrolled();
        fieldsOfTheCourse();
        scored();

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.finalAverage()).isNull();
        assertThat(card.finalAverageInWords()).isEmpty();
        assertThat(card.fields()).isEmpty();
    }

    @Test
    void reportCard_unknownEnrollment_isNotFound() {
        when(enrollmentDomain.courseStudentById(enrollment)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportCard(enrollment))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
