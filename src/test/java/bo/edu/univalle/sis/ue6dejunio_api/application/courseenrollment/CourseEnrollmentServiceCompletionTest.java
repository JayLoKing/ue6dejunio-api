package bo.edu.univalle.sis.ue6dejunio_api.application.courseenrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.courseenrollment.CourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDetails;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A student registered from the name-only spreadsheet is completed later by the SIE roster of the
 * same course. With no RUDE or CI to go by, the row finds them by name among the incomplete
 * students seated in that course, fills only what is missing, and creates nobody.
 */
@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceCompletionTest {

    private static final String EFFECTIVE = "Effective";
    private static final LocalDate BIRTH = LocalDate.of(2015, 3, 1);
    private static final UUID ACTOR = UUID.randomUUID();

    @Mock private IStudentDomain studentDomain;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IAcademicYearDomain academicYearDomain;
    @Mock private ClosedYearGuard closedYearGuard;

    @InjectMocks private CourseEnrollmentService service;

    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void courseExists() {
        lenient().when(enrollmentDomain.courseExists(courseId)).thenReturn(true);
        lenient().when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of());
        lenient().when(studentDomain.findByIdentityCardIn(anyCollection())).thenReturn(List.of());
    }

    /** Seated in the course, as the spreadsheet import left them. */
    private void seatedIncomplete(Student... students) {
        when(studentDomain.findIncompleteInCourse(courseId)).thenReturn(List.of(students));
        Map<UUID, String> seats =
                Arrays.stream(students).collect(Collectors.toMap(Student::getId, s -> EFFECTIVE));
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(seats);
    }

    private static Student nameOnly(String lastNames, String names) {
        return Student.builder()
                .id(UUID.randomUUID())
                .names(names)
                .lastNames(lastNames)
                .status(EFFECTIVE)
                .build();
    }

    private static CreateStudentCommand sieRow(
            String rude, String card, String lastNames, String names) {
        return new CreateStudentCommand(rude, card, names, lastNames, BIRTH, "F");
    }

    private static CreateStudentCommand spreadsheetRow(String lastNames, String names) {
        return new CreateStudentCommand(null, null, names, lastNames, null, null);
    }

    private EnrollResult load(CreateStudentCommand... rows) {
        return service.enroll(new EnrollToCourseCommand(courseId, List.of(rows), ACTOR));
    }

    @Test
    void sieRow_fillsTheIncompleteStudentFoundByName_andCreatesNobody() {
        Student ana = nameOnly("QUISPE MAMANI", "ANA MARIA");
        seatedIncomplete(ana);

        EnrollResult result = load(sieRow("R1", "C1", "Quispe Mamani", "Ana María"));

        assertThat(result.studentsCreated()).isZero();
        assertThat(result.studentsExisting()).isEqualTo(1);
        assertThat(result.enrollmentsSkipped()).isEqualTo(1);
        assertThat(result.completed()).isEqualTo(1);
        verify(studentDomain, never()).save(any());
        verify(studentDomain)
                .updateDetails(
                        ana.getId(),
                        new StudentDetails("R1", "C1", "ANA MARIA", "QUISPE MAMANI", BIRTH, "F"));
    }

    @Test
    void aDifferentSplitOfTheSameFullName_stillMatches() {
        Student ana = nameOnly("QUISPE MAMANI ANA", "MARIA");
        seatedIncomplete(ana);

        EnrollResult result = load(sieRow("R1", "C1", "Quispe Mamani", "Ana Maria"));

        assertThat(result.completed()).isEqualTo(1);
        verify(studentDomain, never()).save(any());
    }

    @Test
    void reimportingTheSpreadsheet_createsNobody_andCompletesNothing() {
        Student ana = nameOnly("QUISPE MAMANI", "ANA MARIA");
        seatedIncomplete(ana);

        EnrollResult result = load(spreadsheetRow("QUISPE MAMANI", "ANA MARIA"));

        assertThat(result.studentsCreated()).isZero();
        assertThat(result.enrollmentsSkipped()).isEqualTo(1);
        assertThat(result.completed()).isZero();
        verify(studentDomain, never()).save(any());
        verify(studentDomain, never()).updateDetails(any(), any());
    }

    @Test
    void aPresentValue_isNeverOverwritten() {
        Student ana = nameOnly("QUISPE", "ANA");
        ana.setBirthDate(BIRTH);
        seatedIncomplete(ana);

        load(new CreateStudentCommand("R1", null, "ANA", "QUISPE", BIRTH, "F"));

        verify(studentDomain)
                .updateDetails(
                        ana.getId(), new StudentDetails("R1", null, "ANA", "QUISPE", BIRTH, "F"));
    }

    /** Two people with the same name: a RUDE already on record that differs is someone else. */
    @Test
    void aCandidateWhoseRudeDiffers_isNotMatched_andTheRowIsCreated() {
        Student ana = nameOnly("QUISPE", "ANA");
        ana.setRudeCode("R-OTHER");
        seatedIncomplete(ana);
        when(studentDomain.save(any())).thenAnswer(i -> withId(i.getArgument(0)));

        EnrollResult result = load(sieRow("R1", "C1", "QUISPE", "ANA"));

        assertThat(result.studentsCreated()).isEqualTo(1);
        assertThat(result.completed()).isZero();
        verify(studentDomain, never()).updateDetails(any(), any());
    }

    @Test
    void spreadsheetRowsRepeatingANameInOneLoad_createOneStudent() {
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of());
        when(studentDomain.save(any())).thenAnswer(i -> withId(i.getArgument(0)));

        EnrollResult result =
                load(spreadsheetRow("QUISPE", "ANA"), spreadsheetRow("Quispe", "Ána"));

        assertThat(result.studentsCreated()).isEqualTo(1);
        assertThat(result.enrollmentsCreated()).isEqualTo(1);
        assertThat(result.enrollmentsSkipped()).isEqualTo(1);
        verify(studentDomain, times(1)).save(any());
    }

    @Test
    void twoIncompleteStudentsWithTheRowsName_isAConflict_notAGuess() {
        Student first = nameOnly("QUISPE", "ANA");
        Student second = nameOnly("Quispe", "Ana");
        seatedIncomplete(first, second);

        EnrollResult result = load(sieRow("R1", "C1", "QUISPE", "ANA"));

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).reason()).contains("QUISPE ANA");
        assertThat(result.studentsCreated()).isZero();
        verify(studentDomain, never()).save(any());
        verify(studentDomain, never()).updateDetails(any(), any());
    }

    @Test
    void enrollOne_withAnAmbiguousName_throws() {
        seatedIncomplete(nameOnly("QUISPE", "ANA"), nameOnly("QUISPE", "ANA"));

        assertThatThrownBy(
                        () ->
                                service.enrollOne(
                                        new EnrollToCourseCommand(
                                                courseId,
                                                List.of(sieRow("R1", "C1", "QUISPE", "ANA")),
                                                ACTOR)))
                .isInstanceOf(ConflictException.class);
    }

    /** A teacher typed the RUDE by hand; the SIE roster then fills the rest by that RUDE. */
    @Test
    void anIncompleteStudentFoundByRude_isCompletedToo() {
        Student ana = nameOnly("QUISPE", "ANA");
        ana.setRudeCode("R1");
        when(studentDomain.findByRudeCodeIn(anyCollection())).thenReturn(List.of(ana));
        when(enrollmentDomain.enrollmentStatusByStudent(eq(courseId), anyCollection()))
                .thenReturn(Map.of(ana.getId(), EFFECTIVE));

        EnrollResult result = load(sieRow("R1", "C1", "QUISPE", "ANA"));

        assertThat(result.completed()).isEqualTo(1);
        verify(studentDomain)
                .updateDetails(
                        ana.getId(), new StudentDetails("R1", "C1", "ANA", "QUISPE", BIRTH, "F"));
    }

    /** The match looks at the course being loaded only, in one query for the whole roster. */
    @Test
    void theIncompleteStudentsAreAskedForOnce() {
        seatedIncomplete(nameOnly("QUISPE", "ANA"));
        List<CreateStudentCommand> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(spreadsheetRow("QUISPE", "ANA"));
        }

        load(rows.toArray(CreateStudentCommand[]::new));

        verify(studentDomain, times(1)).findIncompleteInCourse(courseId);
    }

    private static Student withId(Student s) {
        s.setId(UUID.randomUUID());
        return s;
    }
}
