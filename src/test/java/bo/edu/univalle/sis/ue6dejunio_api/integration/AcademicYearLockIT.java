package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearLockDomain;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The join from a record up to its year, against the real schema: the part mocks cannot prove. */
class AcademicYearLockIT extends AbstractIntegrationTest {

    @Autowired private IAcademicYearLockDomain lock;
    @Autowired private ClosedYearGuard guard;

    private UUID course2026;
    private UUID classGroup2026;
    private UUID enrollment2026;
    private UUID course2025;
    private UUID classGroup2025;
    private UUID enrollment2025;

    @BeforeEach
    void seed() {
        UUID teacher = seedUser("Teacher", false);
        course2026 = seedCourse(teacher, "A", academicYearId(2026));
        classGroup2026 = seedClassGroup(course2026, teacher, "Matematicas");
        enrollment2026 = seedEnrollment(seedStudent(), course2026);
        course2025 = seedCourse(teacher, "B", academicYearId(2025));
        classGroup2025 = seedClassGroup(course2025, teacher, "Matematicas");
        enrollment2025 = seedEnrollment(seedStudent(), course2025);
    }

    private void close(int year) {
        jdbc.update(
                "UPDATE academic_years SET closed_at = now(), active = false WHERE year = ?", year);
    }

    @Test
    void openYears_answerNothing() {
        assertThat(lock.closedYearsOfClassGroups(List.of(classGroup2026, classGroup2025)))
                .isEmpty();
        assertThat(lock.closedYearsOfEnrollments(List.of(enrollment2026))).isEmpty();
        assertThat(lock.closedYearsOfCourses(List.of(course2026))).isEmpty();
    }

    @Test
    void closedYear_isFoundFromClassGroupEnrollmentAndCourse() {
        close(2026);

        assertThat(lock.closedYearsOfClassGroups(List.of(classGroup2026))).containsExactly(2026);
        assertThat(lock.closedYearsOfEnrollments(List.of(enrollment2026))).containsExactly(2026);
        assertThat(lock.closedYearsOfCourses(List.of(course2026))).containsExactly(2026);
    }

    @Test
    void aBatchSpanningTwoYears_reportsOnlyTheClosedOneInOneAnswer() {
        close(2025);

        assertThat(lock.closedYearsOfClassGroups(List.of(classGroup2026, classGroup2025)))
                .isEqualTo(Set.of(2025));
        assertThat(lock.closedYearsOfEnrollments(List.of(enrollment2026, enrollment2025)))
                .isEqualTo(Set.of(2025));
    }

    @Test
    void unknownIds_answerNothing() {
        close(2026);

        assertThat(lock.closedYearsOfClassGroups(List.of(UUID.randomUUID()))).isEmpty();
        assertThat(lock.closedYearsOfEnrollments(List.of(UUID.randomUUID()))).isEmpty();
        assertThat(lock.closedYearsOfCourses(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void guard_refusesAClosedYearAndPassesAnUnknownTarget() {
        close(2026);

        assertThatThrownBy(() -> guard.requireOpenForClassGroup(classGroup2026))
                .isInstanceOf(ConflictException.class)
                .hasMessage("La gestión 2026 está cerrada; no admite cambios.");
        assertThatThrownBy(() -> guard.requireOpenForYear(academicYearId(2026)))
                .isInstanceOf(ConflictException.class);
        guard.requireOpenForClassGroup(UUID.randomUUID());
        guard.requireOpenForClassGroup(classGroup2025);
    }
}
