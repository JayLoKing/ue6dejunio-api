package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.YearSeat;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The one query a roster import asks about where its students already sit in the gestión of the
 * course being loaded. Over a real Postgres because the answer hinges on the course's year, which
 * is a join the port hides.
 */
class EnrollmentSeatsInYearIT extends AbstractIntegrationTest {

    @Autowired private ICourseEnrollmentDomain enrollmentDomain;

    private UUID target;
    private UUID sibling;
    private UUID lastYear;

    @BeforeEach
    void seed() {
        UUID teacher = seedUser("Teacher", false);
        target = seedCourse(teacher, "A");
        sibling = seedCourse(seedUser("Teacher", false), "B");
        lastYear = seedCourse(seedUser("Teacher", false), "C", academicYearId(2025));
    }

    private void withStatus(UUID enrolment, String status) {
        jdbc.update(
                "UPDATE course_enrollments SET status = ? WHERE id_course_enrollment = ?",
                status,
                enrolment);
    }

    private String labelOf(UUID course) {
        return jdbc.queryForObject(
                "SELECT g.name || ' ' || p.name FROM courses c "
                        + "JOIN grades g ON g.id_grade = c.id_grade "
                        + "JOIN parallels p ON p.id_parallel = c.id_parallel "
                        + "WHERE c.id_course = ?",
                String.class,
                course);
    }

    @Test
    void seatsOfTheCoursesOwnGestion_comeBackWithTheirCourseAndStatus() {
        UUID student = seedStudent();
        withStatus(seedEnrollment(student, sibling), "Withdrawn");
        seedEnrollment(student, target);

        List<YearSeat> seats = enrollmentDomain.seatsInYearOfCourse(target, Set.of(student));

        assertThat(seats)
                .containsExactlyInAnyOrder(
                        new YearSeat(student, sibling, labelOf(sibling), "Withdrawn"),
                        new YearSeat(student, target, labelOf(target), "Effective"));
    }

    @Test
    void aSeatInAnotherGestion_isLeftOut() {
        UUID student = seedStudent();
        seedEnrollment(student, lastYear);

        assertThat(enrollmentDomain.seatsInYearOfCourse(target, Set.of(student))).isEmpty();
    }

    @Test
    void onlyTheStudentsAskedAbout_comeBack() {
        UUID asked = seedStudent();
        UUID other = seedStudent();
        seedEnrollment(asked, sibling);
        seedEnrollment(other, sibling);

        assertThat(enrollmentDomain.seatsInYearOfCourse(target, Set.of(asked)))
                .extracting(YearSeat::studentId)
                .containsExactly(asked);
    }

    @Test
    void nobodyToAskAbout_answersEmpty() {
        assertThat(enrollmentDomain.seatsInYearOfCourse(target, Set.of())).isEmpty();
    }
}
