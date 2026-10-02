package bo.edu.univalle.sis.ue6dejunio_api.application.academicyear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.YearResultFreezer;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.FrozenResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** What closing a year writes on the enrollments, with the gradebook and the port mocked. */
@ExtendWith(MockitoExtension.class)
class YearResultFreezerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 2, 12, 0);

    @Mock private ICourseService courseService;
    @Mock private IGradebookService gradebook;
    @Mock private ICourseEnrollmentDomain enrollments;

    @Captor private ArgumentCaptor<Collection<FrozenResult>> frozen;

    private YearResultFreezer freezer;

    @BeforeEach
    void setUp() {
        freezer = new YearResultFreezer(courseService, gradebook, enrollments);
    }

    private static Course course(UUID id) {
        return new Course(id, 1, "1ro", 1, "A", 7, 2026, null, null, true, false);
    }

    private static StudentAnnualSummary summary(UUID enrollmentId, String finalAverage) {
        return new StudentAnnualSummary(
                enrollmentId,
                UUID.randomUUID(),
                "Name",
                List.of(),
                null,
                null,
                null,
                finalAverage == null ? null : new BigDecimal(finalAverage));
    }

    @Test
    void freeze_closesTheEnrollmentsThenFreezesTheCanonicalAverageOfEveryCourse() {
        UUID courseA = UUID.randomUUID();
        UUID courseB = UUID.randomUUID();
        UUID approved = UUID.randomUUID();
        UUID roundedUp = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        UUID ungraded = UUID.randomUUID();
        when(courseService.allOfYear(7)).thenReturn(List.of(course(courseA), course(courseB)));
        when(gradebook.annualSummariesOfCourse(courseA))
                .thenReturn(List.of(summary(approved, "72.00"), summary(roundedUp, "50.60")));
        when(gradebook.annualSummariesOfCourse(courseB))
                .thenReturn(List.of(summary(failed, "50.49"), summary(ungraded, null)));

        freezer.freeze(7, AT);

        InOrder order = inOrder(enrollments);
        order.verify(enrollments).closeEnrollmentsOfYear(7, AT);
        order.verify(enrollments).freezeResults(frozen.capture());
        assertThat(frozen.getValue())
                .containsExactlyInAnyOrder(
                        new FrozenResult(approved, new BigDecimal("72.00"), AnnualResult.APPROVED),
                        new FrozenResult(roundedUp, new BigDecimal("50.60"), AnnualResult.APPROVED),
                        new FrozenResult(failed, new BigDecimal("50.49"), AnnualResult.FAILED),
                        new FrozenResult(ungraded, null, null));
    }

    @Test
    void freeze_writesAllCoursesInASingleBatch() {
        when(courseService.allOfYear(7))
                .thenReturn(List.of(course(UUID.randomUUID()), course(UUID.randomUUID())));
        when(gradebook.annualSummariesOfCourse(any()))
                .thenReturn(List.of(summary(UUID.randomUUID(), "60.00")));

        freezer.freeze(7, AT);

        verify(enrollments).freezeResults(frozen.capture());
        assertThat(frozen.getValue()).hasSize(2);
    }

    @Test
    void freeze_yearWithoutCourses_stillClosesTheEnrollmentsAndWritesNothing() {
        when(courseService.allOfYear(7)).thenReturn(List.of());

        freezer.freeze(7, AT);

        verify(enrollments).closeEnrollmentsOfYear(7, AT);
        verify(enrollments, never()).freezeResults(any());
    }
}
