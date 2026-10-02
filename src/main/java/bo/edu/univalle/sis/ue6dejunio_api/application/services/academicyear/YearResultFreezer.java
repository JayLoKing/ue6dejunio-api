package bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.FrozenResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Freezes, on every enrollment of a year, what the libreta would say today.
 *
 * <p>It owns no transaction: {@link AcademicYearService#close} calls it inside its own, so the
 * year's {@code closed_at} and the frozen results commit or roll back together.
 *
 * <p>Nothing is recomputed here. The average is the {@code finalAverage} the annual centralizer
 * already builds for each course ({@link IGradebookService#annualSummariesOfCourse}), and the
 * result is decided by {@link AnnualResult} on the mark the libreta prints. The cost is the
 * centralizer's own per course (roster page, its count, one batched load of scores) plus one
 * statement to stamp the enrollments and one JDBC batch to write every result; there is no query
 * per enrollment.
 */
@Component
public class YearResultFreezer {

    private final ICourseService courseService;
    private final IGradebookService gradebook;
    private final ICourseEnrollmentDomain enrollments;

    public YearResultFreezer(
            ICourseService courseService,
            IGradebookService gradebook,
            ICourseEnrollmentDomain enrollments) {
        this.courseService = courseService;
        this.gradebook = gradebook;
        this.enrollments = enrollments;
    }

    /**
     * Closes every enrollment of the year at {@code closedAt} and freezes the average and result of
     * the ones in force. A withdrawn enrollment keeps neither (the port never writes one for it),
     * and an enrollment with nothing graded freezes null for both: no data is not a failure.
     */
    public void freeze(int academicYearId, LocalDateTime closedAt) {
        enrollments.closeEnrollmentsOfYear(academicYearId, closedAt);
        List<FrozenResult> results = new ArrayList<>();
        for (Course course : courseService.allOfYear(academicYearId)) {
            for (StudentAnnualSummary summary : gradebook.annualSummariesOfCourse(course.id())) {
                results.add(
                        new FrozenResult(
                                summary.courseEnrollmentId(),
                                summary.finalAverage(),
                                AnnualResult.of(summary.finalAverage())));
            }
        }
        if (!results.isEmpty()) {
            enrollments.freezeResults(results);
        }
    }
}
