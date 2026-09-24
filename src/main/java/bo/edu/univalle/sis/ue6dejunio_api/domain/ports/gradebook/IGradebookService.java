package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseOverview;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.HonorRollEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentReportCard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentTrimesterSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IGradebookService {
    StudentTrimesterSummary studentSummary(UUID courseEnrollmentId, Integer trimester);

    PageResult<StudentTrimesterSummary> centralizer(
            UUID courseId, Integer trimester, PageQuery pageQuery);

    /**
     * The whole year of the course: per-area totals with their average, the three trimester
     * averages, and the final average. No trimester and no gestión — the course pins both.
     */
    PageResult<StudentAnnualSummary> annualCentralizer(UUID courseId, PageQuery pageQuery);

    /**
     * One student's libreta: their year grouped by field of knowledge, with the annual average in
     * figures and in words, and the areas passed and failed in each trimester.
     */
    StudentReportCard reportCard(UUID courseEnrollmentId);

    /**
     * The podium of one course: the {@code places} best final averages, best first.
     *
     * <p>Ordered by the same average the libreta prints, never by one recomputed here. A student
     * with nothing graded holds no place — they were not judged, so they did not come last.
     */
    List<HonorRollEntry> honorRoll(UUID courseId, int places);

    /**
     * The podium of the whole school for one academic year: the {@code places} best final averages
     * across every course, best first, each entry naming the classroom it came from.
     *
     * @param academicYearId the gestión, required. A podium spanning years would rank a student of
     *     2024 against one of 2026, which the school never does.
     */
    List<HonorRollEntry> institutionHonorRoll(Integer academicYearId, int places);

    PageResult<CourseAttendanceRow> courseAttendance(
            UUID courseId, LocalDate date, PageQuery pageQuery);

    /**
     * Per-subject sheet: the active roster of the class group's course, with that class group's
     * session rows only.
     */
    PageResult<CourseAttendanceRow> classGroupAttendance(
            UUID classGroupId, LocalDate date, PageQuery pageQuery);

    CourseOverview courseOverview(UUID courseId, Integer trimester, PageQuery pageQuery);
}
